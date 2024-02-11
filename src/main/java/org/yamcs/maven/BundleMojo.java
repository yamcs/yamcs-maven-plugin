package org.yamcs.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.tar.TarArchiver;
import org.codehaus.plexus.archiver.tar.TarArchiver.TarCompressionMethod;
import org.codehaus.plexus.archiver.tar.TarLongFileMode;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.util.FileUtils;

/**
 * Bundle a Yamcs application into a single archive file.
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BundleMojo extends AbstractYamcsMojo {

    /**
     * The directory that will be used to bundle the Yamcs application.
     */
    @Parameter(defaultValue = "${project.build.directory}/bundle-tmp", required = true, readonly = true)
    private File tempRoot;

    /**
     * Specifies the filename that will be used for the generated bundle. Note that
     * the classifier will be appended to the filename.
     */
    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    private String finalName;

    /**
     * Controls whether this mojo attaches the resulting bundle to the Maven
     * project.
     */
    @Parameter(property = "yamcs.attach", defaultValue = "true")
    private boolean attach;

    /**
     * Classifier to add to the generated bundle.
     */
    @Parameter(defaultValue = "bundle")
    private String classifier;

    /**
     * Whether 'yamcs' and 'yamcsadmin' wrapper scripts should be included in the
     * bundle. Set to <code>false</code> when bundling a reusable extension instead
     * of an application.
     */
    @Parameter(property = "yamcs.includeDefaultWrappers", defaultValue = "true")
    private boolean includeDefaultWrappers;

    /**
     * Whether this module's configuration directory (default location:
     * <code>src/main/yamcs</code>)
     * should be included in the bundle.
     */
    @Parameter(property = "yamcs.includeConfiguration", defaultValue = "true")
    private boolean includeConfiguration;

    /**
     * Set whether the default excludes are being applied. Defaults to true.
     */
    @Parameter(defaultValue = "true")
    private boolean useDefaultExcludes;

    /**
     * Set a string of patterns, which included files should match.
     */
    @Parameter()
    private List<String> includes;

    /**
     * Set a string of patterns, which excluded files should match.
     */
    @Parameter()
    private List<String> excludes;

    /**
     * Specifies the formats of the bundle. Multiple formats can be supplied. Each
     * format is specified by supplying one of the following values in a
     * &lt;format&gt; subelement:
     * <ul>
     * <li><em>zip</em> - Creates a ZIP file format</li>
     * <li><em>tar</em> - Creates a TAR format</li>
     * <li><em>tar.gz</em> or <em>tgz</em> - Creates a gzip'd TAR format</li>
     * <li><em>tar.bz2</em> or <em>tbz2</em> - Creates a bzip'd TAR format</li>
     * <li><em>tar.snappy</em> - Creates a snappy'd TAR format</li>
     * <li><em>tar.xz</em> or <em>txz</em> - Creates a xz'd TAR format</li>
     * </ul>
     * 
     * If unspecified the behavior is equivalent to:
     * 
     * <pre>
     * &lt;formats&gt;
     *   &lt;format&gt;tar.gz&lt;/format&gt;
     * &lt;/formats&gt;
     * </pre>
     */
    @Parameter
    private List<String> formats;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping execution");
            return;
        }

        Artifact projectArtifact = project.getArtifact();
        File projectFile = projectArtifact.getFile();

        // If the project for this mojo is of type "jar", then we expect to find
        // a jar file to be included in the generated lib/ directory
        if (projectArtifact.getType().equals("jar")) {
            if (projectFile == null) {
                throw new MojoExecutionException("Could not find main project artifact. This is usually"
                        + " solved by running 'package' before 'yamcs:bundle', or by adding a 'bundle'"
                        + " goal to the execution configuration of the yamcs-maven-plugin");
            }
        }

        List<String> effectiveFormats = formats;
        if (effectiveFormats == null) {
            effectiveFormats = Arrays.asList("tar.gz");
        } else if (effectiveFormats.isEmpty()) {
            throw new MojoFailureException("No format specified for Yamcs bundle");
        }

        prepareBundle(projectFile);
        for (String format : effectiveFormats) {
            File compressedBundle = compressBundle(format);
            if (attach) {
                projectHelper.attachArtifact(project, format, classifier, compressedBundle);
            }
        }
    }

    private void prepareBundle(File projectFile) throws MojoExecutionException {
        try {
            FileUtils.deleteDirectory(tempRoot);
            tempRoot.mkdirs();
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot prepare bundle directory", e);
        }

        if (includeConfiguration) {
            try {
                initConfiguration(tempRoot);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot create configuration", e);
            }
        }

        File libDirectory = new File(tempRoot, "lib");
        libDirectory.mkdirs();

        List<File> libFiles = new ArrayList<>();
        if (projectFile != null) {
            libFiles.add(projectFile);
        }
        List<String> scopes = Arrays.asList("compile", "runtime");
        libFiles.addAll(getDependencyFiles(scopes));

        try {
            for (File file : libFiles) {
                FileUtils.copyFileToDirectory(file, libDirectory);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot build lib directory", e);
        }

        if (includeDefaultWrappers) {
            try {
                File binDirectory = new File(tempRoot, "bin");
                binDirectory.mkdirs();
                copyWrappers(binDirectory);
            } catch (IOException e) {
                throw new MojoExecutionException("Could not copy wrappers", e);
            }
        }
    }

    private void copyWrappers(File binDirectory) throws IOException {
        copyExecutableResource("/yamcsd", new File(binDirectory, "yamcsd"));
        copyExecutableResource("/yamcsd.cmd", new File(binDirectory, "yamcsd.cmd"));
        copyExecutableResource("/yamcsadmin", new File(binDirectory, "yamcsadmin"));
        copyExecutableResource("/yamcsadmin.cmd", new File(binDirectory, "yamcsadmin.cmd"));
    }

    private File compressBundle(String format) throws MojoExecutionException {
        Archiver archiver;
        try {
            if (format.equals("tar")) {
                archiver = archiverManager.getArchiver("tar");
                ((TarArchiver) archiver).setCompression(TarCompressionMethod.none);
                ((TarArchiver) archiver).setLongfile(TarLongFileMode.posix);
            } else if (format.equals("tar.gz") || format.equals("tgz")) {
                archiver = archiverManager.getArchiver("tar");
                ((TarArchiver) archiver).setCompression(TarCompressionMethod.gzip);
                ((TarArchiver) archiver).setLongfile(TarLongFileMode.posix);
            } else if (format.equals("tar.bz2") || format.equals("tbz2")) {
                archiver = archiverManager.getArchiver("tar");
                ((TarArchiver) archiver).setCompression(TarCompressionMethod.bzip2);
                ((TarArchiver) archiver).setLongfile(TarLongFileMode.posix);
            } else if (format.equals("tar.snappy")) {
                archiver = archiverManager.getArchiver("tar");
                ((TarArchiver) archiver).setCompression(TarCompressionMethod.snappy);
                ((TarArchiver) archiver).setLongfile(TarLongFileMode.posix);
            } else if (format.equals("tar.xz") || format.equals("xz")) {
                archiver = archiverManager.getArchiver("tar");
                ((TarArchiver) archiver).setCompression(TarCompressionMethod.xz);
                ((TarArchiver) archiver).setLongfile(TarLongFileMode.posix);
            } else if (format.equals("zip")) {
                archiver = archiverManager.getArchiver("zip");
            } else {
                throw new MojoExecutionException("Unsupported bundle format '" + format + "''");
            }
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("Can't find tar.gz archiver", e);
        }

        archiver.setIgnorePermissions(false);

        String filename;
        if (classifier == null && "".equals(classifier)) {
            filename = finalName + "." + format;
        } else {
            filename = finalName + "-" + classifier + "." + format;
        }
        File destFile = new File(target, filename);
        archiver.setDestFile(destFile);

        DefaultFileSet fileSet = new DefaultFileSet(tempRoot);
        fileSet.setUsingDefaultExcludes(useDefaultExcludes);
        if (includes != null) {
            fileSet.setIncludes(includes.toArray(new String[0]));
        }
        if (excludes != null) {
            fileSet.setExcludes(excludes.toArray(new String[0]));
        }
        fileSet.prefixed(finalName + "/");
        archiver.addFileSet(fileSet);
        try {
            archiver.createArchive();
        } catch (ArchiverException | IOException e) {
            throw new MojoExecutionException("Cannot create archive", e);
        }
        return destFile;
    }
}
