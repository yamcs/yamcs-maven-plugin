package org.yamcs.maven;

import static java.lang.Math.max;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static org.codehaus.plexus.util.FileUtils.cleanDirectory;
import static org.codehaus.plexus.util.FileUtils.copyStreamToFile;
import static org.codehaus.plexus.util.FileUtils.getDefaultExcludesAsString;
import static org.codehaus.plexus.util.FileUtils.getFiles;
import static org.codehaus.plexus.util.StringUtils.join;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.io.RawInputStreamFacade;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * This mojo executes the {@code protoc} compiler for generating main Java
 * sources from protocol buffer definitions. It also searches dependency
 * artifacts for {@code .proto} files and includes them
 * in the {@code proto_path} so that they can be referenced. Finally, it adds
 * the {@code .proto} files to the project
 * as resources so that they are included in the final artifact.
 */
@Mojo(name = "protoc", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class ProtocMojo extends AbstractMojo {

    private static final String PROTO_FILE_SUFFIX = ".proto";
    private static final String DEFAULT_INCLUDES = "**/*" + PROTO_FILE_SUFFIX;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    @Component
    protected BuildContext buildContext;

    @Component
    protected MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ResolutionErrorHandler resolutionErrorHandler;

    @Parameter(required = true, readonly = true, property = "localRepository")
    private ArtifactRepository localRepository;

    @Parameter(required = true, readonly = true, defaultValue = "${project.remoteArtifactRepositories}")
    private List<ArtifactRepository> remoteRepositories;

    /**
     * A directory where native launchers for java protoc plugins will be generated.
     */
    @Parameter(required = false, defaultValue = "${project.build.directory}/protoc-plugins")
    private File protocPluginDirectory;

    @Parameter(required = false, property = "protocVersion", defaultValue = "3.19.4")
    private String protocVersion;

    /**
     * Since {@code protoc} cannot access jars, proto files in dependencies are
     * extracted to this location and deleted
     * on exit. This directory is always cleaned during execution.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/protoc-dependencies")
    private File temporaryProtoFileDirectory;

    /**
     * A list of &lt;include&gt; elements specifying the protobuf definition files
     * (by pattern) that should be included
     * in compilation. When not specified, the default includes will be: <code><br/>
     * &lt;includes&gt;<br/>
     * &nbsp;&lt;include&gt;**&#47;*.proto&lt;/include&gt;<br/>
     * &lt;/includes&gt;<br/>
     * </code>
     */
    @Parameter(required = false)
    private String[] includes = { DEFAULT_INCLUDES };

    /**
     * A list of &lt;exclude&gt; elements specifying the protobuf definition files
     * (by pattern) that should be excluded
     * from compilation. When not specified, the default excludes will be empty:
     * <code><br/>
     * &lt;excludes&gt;<br/>
     * &lt;/excludes&gt;<br/>
     * </code>
     */
    @Parameter(required = false)
    private String[] excludes = {};

    /**
     * If set to {@code true}, then the specified protobuf source files from this
     * project will be attached as resources
     * to the build, for subsequent inclusion into the final artifact. This is the
     * default behaviour, as it allows
     * downstream projects to import protobuf definitions from the upstream
     * projects, and those imports are
     * automatically resolved at build time.
     */
    @Parameter(required = true, defaultValue = "true")
    protected boolean attachProtoSources;

    /**
     * The descriptor set file name. Only used if {@code writeDescriptorSet} is set
     * to {@code true}.
     */
    @Parameter(required = true, defaultValue = "${project.artifactId}.protobin")
    protected String descriptorSetFileName;

    /**
     * If set to {@code true}, the compiler will generate a binary descriptor set
     * file for the specified {@code .proto}
     * files.
     */
    @Parameter(required = true, defaultValue = "false")
    protected boolean writeDescriptorSet;

    /**
     * If set to {@code true}, the generated descriptor set will be attached to the
     * build.
     */
    @Parameter(required = true, defaultValue = "false")
    protected boolean attachDescriptorSet;

    /**
     * If {@code true} and {@code writeDescriptorSet} has been set, the compiler
     * will include all dependencies in the descriptor set making it
     * "self-contained".
     */
    @Parameter(required = false, defaultValue = "true")
    protected boolean includeDependenciesInDescriptorSet;

    /**
     * If {@code true} and {@code writeDescriptorSet} has been set, do not strip
     * SourceCodeInfo from the FileDescriptorProto. This results in vastly larger
     * descriptors that include information about the original location of each decl
     * in the source file as well as surrounding comments.
     */
    @Parameter(required = false, defaultValue = "true")
    protected boolean includeSourceInfoInDescriptorSet;

    /**
     * Sets the granularity in milliseconds of the last modification date for
     * testing whether source protobuf definitions need recompilation.
     *
     * <p>
     * This parameter is only used when {@link #checkStaleness} parameter is set to
     * {@code true}.
     *
     * <p>
     * If the project is built on NFS it's recommended to set this parameter to
     * {@code 10000}.
     */
    @Parameter(required = false, defaultValue = "0")
    private long staleMillis;

    /**
     * Normally {@code protoc} is invoked on every execution of the plugin. Setting
     * this parameter to {@code true} will enable checking timestamps of source
     * protobuf definitions vs. generated sources.
     *
     * @see #staleMillis
     */
    @Parameter(required = false, defaultValue = "true")
    private boolean checkStaleness;

    /**
     * When {@code true}, skip the execution.
     */
    @Parameter(required = false, property = "protoc.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Usually most of protobuf mojos will not get executed on parent poms (i.e.
     * projects with packaging type 'pom').
     * Setting this parameter to {@code true} will force the execution of this mojo,
     * even if it would usually get
     * skipped in this case.
     */
    @Parameter(required = false, property = "protoc.force", defaultValue = "false")
    private boolean forceMojoExecution;

    /**
     * When {@code true}, the output directory will be cleared out prior to code
     * generation. With the latest versions of
     * protoc (2.5.0 or later) this is generally not required, although some earlier
     * versions reportedly had issues with
     * running two code generations in a row without clearing out the output
     * directory in between.
     */
    @Parameter(required = false, defaultValue = "true")
    private boolean clearOutputDirectory;

    /**
     * The source directories containing the {@code .proto} definitions to be
     * compiled.
     */
    @Parameter(required = true, defaultValue = "${basedir}/src/main/proto")
    private File protoSourceRoot;

    /**
     * This is the directory into which the (optional) descriptor set file will be
     * created.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-resources/protobuf")
    private File descriptorSetOutputDirectory;

    /**
     * If generated descriptor set is to be attached to the build, specifies an
     * optional classifier.
     */
    @Parameter(required = false)
    protected String descriptorSetClassifier;

    @Parameter(required = true, property = "javaOutputDirectory", defaultValue = "${project.build.directory}/generated-sources/protobuf/java")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipMojo()) {
            return;
        }

        if (protoSourceRoot.exists()) {
            try {
                List<File> protoFiles = findProtoFilesInDirectory(protoSourceRoot);
                List<File> outputFiles = findGeneratedFilesInDirectory(outputDirectory);

                if (protoFiles.isEmpty()) {
                    getLog().info("No proto files to compile.");
                } else if (!hasDelta(protoFiles)) {
                    getLog().info("Skipping compilation because build context has no changes.");
                    doAttachFiles();
                } else if (checkStaleness && checkFilesUpToDate(protoFiles, outputFiles)) {
                    getLog().info("Skipping compilation because target directory newer than sources.");
                    doAttachFiles();
                } else {
                    List<File> derivedProtoPathElements = makeProtoPathFromJars(temporaryProtoFileDirectory,
                            getDependencyArtifactFiles());
                    FileUtils.mkdir(outputDirectory.getAbsolutePath());

                    if (clearOutputDirectory) {
                        try {
                            cleanDirectory(outputDirectory);
                        } catch (IOException e) {
                            throw new MojoInitializationException("Unable to clean output directory", e);
                        }
                    }

                    if (writeDescriptorSet) {
                        File descriptorSetOutputDirectory = getDescriptorSetOutputDirectory();
                        FileUtils.mkdir(descriptorSetOutputDirectory.getAbsolutePath());
                        if (clearOutputDirectory) {
                            try {
                                cleanDirectory(descriptorSetOutputDirectory);
                            } catch (IOException e) {
                                throw new MojoInitializationException(
                                        "Unable to clean descriptor set output directory", e);
                            }
                        }
                    }

                    var pluginExecutable = createPluginExecutable();

                    Artifact artifact = createProtocArtifact();
                    File file = resolveBinaryArtifact(artifact);
                    String protocExecutable = file.getAbsolutePath();

                    Protoc.Builder protocBuilder = new Protoc.Builder(protocExecutable)
                            .addProtoPathElement(protoSourceRoot)
                            .addProtoPathElements(derivedProtoPathElements)
                            .addProtoFiles(protoFiles)
                            .setPluginExecutable(pluginExecutable);

                    if (writeDescriptorSet) {
                        File descriptorSetFile = new File(getDescriptorSetOutputDirectory(), descriptorSetFileName);
                        getLog().info("Writing descriptor set: " + descriptorSetFile.getAbsolutePath());
                        protocBuilder.withDescriptorSetFile(
                                descriptorSetFile,
                                includeDependenciesInDescriptorSet,
                                includeSourceInfoInDescriptorSet);
                    }
                    protocBuilder.setJavaOutputDirectory(outputDirectory);

                    Protoc protoc = protocBuilder.build();

                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Proto source root:");
                        getLog().debug(" " + protoSourceRoot);

                        if (derivedProtoPathElements != null && !derivedProtoPathElements.isEmpty()) {
                            getLog().debug("Derived proto paths:");
                            for (File path : derivedProtoPathElements) {
                                getLog().debug(" " + path);
                            }
                        }
                    }
                    protoc.logExecutionParameters(getLog());

                    getLog().info(format("Compiling %d proto file(s) to %s", protoFiles.size(), outputDirectory));

                    int exitStatus = protoc.execute(getLog());
                    if (StringUtils.isNotBlank(protoc.getOutput())) {
                        getLog().info("PROTOC: " + protoc.getOutput());
                    }
                    if (exitStatus != 0) {
                        getLog().error("PROTOC FAILED: " + protoc.getError());
                        for (File pf : protoFiles) {
                            buildContext.removeMessages(pf);
                            buildContext.addMessage(pf, 0, 0, protoc.getError(), BuildContext.SEVERITY_ERROR, null);
                        }
                        throw new MojoFailureException(
                                "protoc did not exit cleanly. Review output for more information.");
                    } else if (StringUtils.isNotBlank(protoc.getError())) {
                        getLog().warn("PROTOC: " + protoc.getError());
                    }
                    doAttachFiles();
                }
            } catch (MojoConfigurationException e) {
                throw new MojoExecutionException("Configuration error: " + e.getMessage(), e);
            } catch (MojoInitializationException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (CommandLineException e) {
                throw new MojoExecutionException("An error occurred while invoking protoc: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            getLog().info(format("%s does not exist. Review the configuration or consider disabling the plugin.",
                    protoSourceRoot));
        }
    }

    private File createPluginExecutable() {
        protocPluginDirectory.mkdirs();

        File targetFile;
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            targetFile = new File(protocPluginDirectory, "protoc-gen-yamcs.cmd");
        } else {
            targetFile = new File(protocPluginDirectory, "protoc-gen-yamcs");
        }

        var generatorPath = ServiceGenerator.class.getName().replace('.', '/') + ".java";
        var javaFile = Path.of(targetFile.getParent(), generatorPath);

        try {
            Files.createDirectories(javaFile.getParent());

            var resource = "/" + generatorPath;
            try (var reader = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream(resource)))) {
                var java = reader.lines().collect(Collectors.joining("\n"));
                Files.writeString(javaFile, java);
            }
        } catch (IOException e) {
            throw new MojoInitializationException("Failed to write protoc plugin source");
        }

        Artifact protocPluginArtifact = repositorySystem.createArtifact(
                "com.google.protobuf",
                "protobuf-java",
                protocVersion,
                "jar");
        protocPluginArtifact.setScope(Artifact.SCOPE_RUNTIME);

        ArtifactResolutionRequest request = new ArtifactResolutionRequest()
                .setArtifact(project.getArtifact())
                .setResolveRoot(false)
                .setArtifactDependencies(Collections.singleton(protocPluginArtifact))
                .setManagedVersionMap(emptyMap())
                .setLocalRepository(localRepository)
                .setRemoteRepositories(remoteRepositories)
                .setOffline(session.isOffline())
                .setForceUpdate(session.getRequest().isUpdateSnapshots())
                .setServers(session.getRequest().getServers())
                .setMirrors(session.getRequest().getMirrors())
                .setProxies(session.getRequest().getProxies());

        ArtifactResolutionResult result = repositorySystem.resolve(request);

        try {
            resolutionErrorHandler.throwErrors(request, result);
        } catch (ArtifactResolutionException e) {
            throw new MojoInitializationException("Unable to resolve plugin artifact: " + e.getMessage(), e);
        }

        Set<Artifact> artifacts = result.getArtifacts();

        if (artifacts == null || artifacts.isEmpty()) {
            throw new MojoInitializationException("Unable to resolve plugin artifact");
        }

        var jarFiles = new ArrayList<File>();
        for (Artifact artifact : artifacts) {
            jarFiles.add(artifact.getFile());
        }

        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            buildWindowsPlugin(javaFile, jarFiles, targetFile);
        } else {
            buildUnixPlugin(javaFile, jarFiles, targetFile);
            targetFile.setExecutable(true);
        }

        return targetFile;
    }

    private void buildUnixPlugin(Path javaFile, List<File> jarFiles, File targetFile) {
        File javaLocation = JavaProcessBuilder.findJava();

        try (var out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("#!/bin/sh");
            out.println();
            out.print("CP=");
            out.print(jarFiles.stream()
                    .map(j -> "\"" + j.getAbsolutePath() + "\"")
                    .collect(Collectors.joining(":")));
            out.println();
            out.println("\"" + javaLocation.getAbsolutePath() + "\" -cp $CP " + javaFile);
        } catch (IOException e) {
            throw new MojoInitializationException("Could not write plugin script file: " + targetFile, e);
        }
    }

    private void buildWindowsPlugin(Path javaFile, List<File> jarFiles, File targetFile) {
        File javaLocation = JavaProcessBuilder.findJava();

        try (var out = new PrintWriter(new FileWriter(targetFile))) {
            out.println("@echo off");
            out.println("setlocal");
            out.println();
            out.print("set CP=");
            out.print(jarFiles.stream()
                    .map(j -> "\"" + j.getAbsolutePath() + "\"")
                    .collect(Collectors.joining(";")));
            out.println();
            out.println("\"" + javaLocation.getAbsolutePath() + "\" -cp %CP% " + javaFile);
        } catch (IOException e) {
            throw new MojoInitializationException("Could not write plugin script file: " + targetFile, e);
        }
    }

    private boolean skipMojo() {
        if (skip) {
            getLog().info("Skipping mojo execution");
            return true;
        }

        if (!forceMojoExecution && "pom".equals(this.project.getPackaging())) {
            getLog().info("Skipping mojo execution for project with packaging type 'pom'");
            return true;
        }

        return false;
    }

    private static List<File> findGeneratedFilesInDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return emptyList();
        }

        List<File> generatedFilesInDirectory;
        try {
            generatedFilesInDirectory = getFiles(directory, "**/*", getDefaultExcludesAsString());
        } catch (IOException e) {
            throw new MojoInitializationException("Unable to scan output directory", e);
        }
        return generatedFilesInDirectory;
    }

    /**
     * Returns timestamp for the most recently modified file in the given set.
     *
     * @param files
     *              a collection of file descriptors.
     * @return timestamp of the most recently modified file.
     */
    private static long lastModified(Iterable<File> files) {
        long result = 0L;
        for (File file : files) {
            result = max(result, file.lastModified());
        }
        return result;
    }

    /**
     * Checks that the source files don't have modification time that is later than
     * the target files.
     *
     * @param sourceFiles
     *                    a collection of source files.
     * @param targetFiles
     *                    a collection of target files.
     * @return {@code true}, if source files are not later than the target files;
     *         {@code false}, otherwise.
     */
    private boolean checkFilesUpToDate(Iterable<File> sourceFiles, Iterable<File> targetFiles) {
        return lastModified(sourceFiles) + staleMillis < lastModified(targetFiles);
    }

    /**
     * Checks if the injected build context has changes in any of the specified
     * files.
     *
     * @param files
     *              files to be checked for changes.
     * @return {@code true}, if at least one file has changes; {@code false}, if no
     *         files have changes.
     */
    private boolean hasDelta(Iterable<File> files) {
        for (File file : files) {
            if (buildContext.hasDelta(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns output directory for descriptor set file. Depends on build phase so
     * must be defined in concrete
     * implementation.
     *
     * @return output directory for generated descriptor set.
     */
    protected File getDescriptorSetOutputDirectory() {
        return descriptorSetOutputDirectory;
    }

    protected void doAttachFiles() {
        if (attachProtoSources) {
            doAttachProtoSources();
        }
        doAttachGeneratedFiles();
    }

    protected void doAttachProtoSources() {
        projectHelper.addResource(project, protoSourceRoot.getAbsolutePath(),
                asList(includes), asList(excludes));
    }

    protected void doAttachGeneratedFiles() {
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        if (writeDescriptorSet) {
            File descriptorSetFile = new File(getDescriptorSetOutputDirectory(), descriptorSetFileName);
            projectHelper.attachArtifact(project, "protobin", descriptorSetClassifier, descriptorSetFile);
        }
        buildContext.refresh(outputDirectory);
    }

    /**
     * Gets the {@link File} for each dependency artifact.
     *
     * @return A list of all dependency artifacts.
     */
    protected List<File> getDependencyArtifactFiles() {
        List<String> compileScopes = Arrays.asList("compile", "provided", "system");
        List<Artifact> dependencyArtifacts = project.getArtifacts().stream()
                .filter(a -> compileScopes.contains(a.getScope()))
                .collect(Collectors.toList());
        List<File> dependencyArtifactFiles = new ArrayList<>(dependencyArtifacts.size());
        for (Artifact artifact : dependencyArtifacts) {
            dependencyArtifactFiles.add(artifact.getFile());
        }
        return dependencyArtifactFiles;
    }

    /**
     * Unpacks proto descriptors that are bundled inside dependent artifacts into a
     * temporary directory. This is needed
     * because protobuf compiler cannot handle imported descriptors that are packed
     * inside jar files.
     *
     * @param temporaryProtoFileDirectory
     *                                    temporary directory to serve as root for
     *                                    unpacked structure.
     * @param classpathElementFiles
     *                                    classpath elements, can be either jar
     *                                    files or directories.
     * @return a list of import roots for protobuf compiler (these will all be
     *         subdirectories of the temporary
     *         directory).
     */
    protected List<File> makeProtoPathFromJars(File temporaryProtoFileDirectory, Iterable<File> classpathElementFiles) {
        if (!classpathElementFiles.iterator().hasNext()) {
            return emptyList();
        }
        // clean the temporary directory to ensure that stale files aren't used
        if (temporaryProtoFileDirectory.exists()) {
            try {
                cleanDirectory(temporaryProtoFileDirectory);
            } catch (IOException e) {
                throw new MojoInitializationException("Unable to clean up temporary proto file directory", e);
            }
        }
        List<File> protoDirectories = new ArrayList<>();
        for (File classpathElementFile : classpathElementFiles) {
            if (classpathElementFile.isFile() && classpathElementFile.canRead() &&
                    classpathElementFile.getName().endsWith(".jar")) {

                // create the jar file. the constructor validates.
                try (JarFile classpathJar = new JarFile(classpathElementFile)) {
                    Enumeration<JarEntry> jarEntries = classpathJar.entries();
                    while (jarEntries.hasMoreElements()) {
                        JarEntry jarEntry = jarEntries.nextElement();
                        String jarEntryName = jarEntry.getName();
                        if (!jarEntry.isDirectory()
                                && SelectorUtils.matchPath(DEFAULT_INCLUDES, jarEntryName, "/", true)) {
                            File jarDirectory;
                            try {
                                jarDirectory = new File(temporaryProtoFileDirectory,
                                        truncatePath(classpathJar.getName()));
                                File uncompressedCopy = new File(jarDirectory, jarEntryName);
                                FileUtils.mkdir(uncompressedCopy.getParentFile().getAbsolutePath());
                                copyStreamToFile(new RawInputStreamFacade(classpathJar.getInputStream(jarEntry)),
                                        uncompressedCopy);
                            } catch (IOException e) {
                                throw new MojoInitializationException("Unable to unpack proto files", e);
                            }
                            protoDirectories.add(jarDirectory);
                        }
                    }
                } catch (IOException e) {
                    throw new MojoInitializationException(
                            "Not a readable JAR artifact: " + classpathElementFile.getAbsolutePath(), e);
                }
            } else if (classpathElementFile.isDirectory()) {
                List<File> protoFiles;
                try {
                    protoFiles = getFiles(classpathElementFile, DEFAULT_INCLUDES, null);
                } catch (IOException e) {
                    throw new MojoInitializationException(
                            "Unable to scan for proto files in: " + classpathElementFile.getAbsolutePath(), e);
                }
                if (!protoFiles.isEmpty()) {
                    protoDirectories.add(classpathElementFile);
                }
            }
        }
        return protoDirectories;
    }

    private List<File> findProtoFilesInDirectory(File directory) {
        if (directory == null) {
            throw new MojoConfigurationException("'directory' is null");
        }
        if (!directory.isDirectory()) {
            throw new MojoConfigurationException(format("%s is not a directory", directory));
        }
        List<File> protoFilesInDirectory;
        try {
            String includesString = join(includes, ",");
            String excludesString = join(excludes, ",");
            protoFilesInDirectory = getFiles(directory, includesString, excludesString);
        } catch (IOException e) {
            throw new MojoInitializationException("Unable to retrieve the list of files: " + e.getMessage(), e);
        }
        return protoFilesInDirectory;
    }

    /**
     * Truncates the path of jar files so that they are relative to the local
     * repository.
     *
     * @param jarPath
     *                the full path of a jar file.
     * @return the truncated path relative to the local repository or root of the
     *         drive.
     */
    private String truncatePath(String jarPath) {
        String repository = localRepository.getBasedir().replace('\\', '/');
        if (!repository.endsWith("/")) {
            repository += "/";
        }

        String path = jarPath.replace('\\', '/');
        int repositoryIndex = path.indexOf(repository);
        if (repositoryIndex != -1) {
            path = path.substring(repositoryIndex + repository.length());
        }

        // By now the path should be good, but do a final check to fix windows machines.
        int colonIndex = path.indexOf(':');
        if (colonIndex != -1) {
            // 2 = :\ in C:\
            path = path.substring(colonIndex + 2);
        }

        return path;
    }

    private File resolveBinaryArtifact(Artifact artifact) {
        ArtifactResolutionResult result;
        try {
            ArtifactResolutionRequest request = new ArtifactResolutionRequest()
                    .setArtifact(project.getArtifact())
                    .setResolveRoot(false)
                    .setResolveTransitively(false)
                    .setArtifactDependencies(singleton(artifact))
                    .setManagedVersionMap(emptyMap())
                    .setLocalRepository(localRepository)
                    .setRemoteRepositories(remoteRepositories)
                    .setOffline(session.isOffline())
                    .setForceUpdate(session.getRequest().isUpdateSnapshots())
                    .setServers(session.getRequest().getServers())
                    .setMirrors(session.getRequest().getMirrors())
                    .setProxies(session.getRequest().getProxies());

            result = repositorySystem.resolve(request);

            resolutionErrorHandler.throwErrors(request, result);
        } catch (ArtifactResolutionException e) {
            throw new MojoInitializationException("Unable to resolve artifact: " + e.getMessage(), e);
        }

        Set<Artifact> artifacts = result.getArtifacts();

        if (artifacts == null || artifacts.isEmpty()) {
            throw new MojoInitializationException("Unable to resolve artifact");
        }

        Artifact resolvedBinaryArtifact = artifacts.iterator().next();
        if (getLog().isDebugEnabled()) {
            getLog().debug("Resolved artifact: " + resolvedBinaryArtifact);
        }

        // Copy the file to the project build directory and make it executable
        File sourceFile = resolvedBinaryArtifact.getFile();
        String sourceFileName = sourceFile.getName();
        String targetFileName;
        if (Os.isFamily(Os.FAMILY_WINDOWS) && !sourceFileName.endsWith(".exe")) {
            targetFileName = sourceFileName + ".exe";
        } else {
            targetFileName = sourceFileName;
        }
        File targetFile = new File(protocPluginDirectory, targetFileName);
        if (targetFile.exists()) {
            // The file must have already been copied in a prior plugin execution/invocation
            getLog().debug("Executable file already exists: " + targetFile.getAbsolutePath());
            return targetFile;
        }
        try {
            FileUtils.forceMkdir(protocPluginDirectory);
        } catch (IOException e) {
            throw new MojoInitializationException("Unable to create directory " + protocPluginDirectory, e);
        }
        try {
            FileUtils.copyFile(sourceFile, targetFile);
        } catch (IOException e) {
            throw new MojoInitializationException("Unable to copy the file to " + protocPluginDirectory, e);
        }
        if (!Os.isFamily(Os.FAMILY_WINDOWS)) {
            targetFile.setExecutable(true);
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Executable file: " + targetFile.getAbsolutePath());
        }
        return targetFile;
    }

    private Artifact createProtocArtifact() {
        String classifier;
        if (Os.isFamily(Os.FAMILY_MAC)) {
            classifier = "osx-";
        } else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            classifier = "windows-";
        } else {
            classifier = "linux-";
        }

        if (Os.isArch("aarch64")) {
            classifier += "aarch_64";
        } else if (Os.isArch("x86_64") || Os.isArch("amd64") || Os.isArch("x64")) {
            classifier += "x86_64";
        } else if (Os.isArch("ppc64le")) {
            classifier += "ppcle_64";
        } else {
            throw new MojoInitializationException("Unexpected architecture: " + System.getProperty("os.arch"));
        }

        Artifact artifact = repositorySystem.createArtifactWithClassifier(
                "com.google.protobuf",
                "protoc",
                protocVersion,
                "exe",
                classifier);
        artifact.setScope(Artifact.SCOPE_RUNTIME);
        return artifact;
    }
}
