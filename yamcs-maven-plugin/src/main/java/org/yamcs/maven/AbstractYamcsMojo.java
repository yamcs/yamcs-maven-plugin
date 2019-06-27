package org.yamcs.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Collects general Maven functionalities for use in specific Mojos
 */
public abstract class AbstractYamcsMojo extends AbstractMojo {

    /**
     * Skip execution
     */
    @Parameter(property = "yamcs.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * The directory that contains Yamcs configuration files. By convention this
     * contains subfolders named <code>etc</code> and <code>mdb</code>.
     * <p>
     * Relative paths in yaml configuration files are resolved from this directory.
     */
    @Parameter(property = "yamcs.configurationDirectory", defaultValue = "${basedir}/src/main/yamcs")
    protected File configurationDirectory;

    /**
     * The path of the Yamcs logging configuration. This file must be in Java Util
     * Logging format.
     */
    @Parameter(property = "yamcs.loggingFile")
    private File loggingFile;

    /**
     * The path of the file describing UTC-TAI offsets history. This file must be in
     * <a href="http://hpiers.obspm.fr/eoppc/bul/bulc/UTC-TAI.history">IERS format</a>.
     * <p>
     * If unspecified, Yamcs uses default offsets that were
     * valid at the time of release.
     */
    @Parameter(property = "yamcs.utcTaiHistory")
    private File utcTaiHistory;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File outputDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected File classesDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Component
    protected RepositorySystem repositorySystem;

    @Component
    protected ArchiverManager archiverManager;

    @Component
    protected MavenProjectHelper projectHelper;

    protected void initConfiguration(File directory) throws IOException {
        directory.mkdirs();

        // Some default config files from yamcs-core
        File etcDir = new File(directory, "etc");
        etcDir.mkdir();
        File targetLoggingFile = new File(etcDir, "logging.properties");
        copyResource("/logging.properties", targetLoggingFile);
        File targetUtcTaiHistory = new File(etcDir, "UTC-TAI.history");
        copyResource("/UTC-TAI.history", targetUtcTaiHistory);

        // Override with configuration from this plugin
        if (configurationDirectory.exists()) {
            FileUtils.copyDirectoryStructure(configurationDirectory, directory);
        }

        // Override further if maven properties were set
        if (loggingFile != null) {
            FileUtils.copyFile(loggingFile, targetLoggingFile);
        }
        if (utcTaiHistory != null) {
            FileUtils.copyFile(utcTaiHistory, targetUtcTaiHistory);
        }
    }

    protected void copyResource(String resource, File file) throws IOException {
        URL url = getClass().getResource(resource);
        if (url == null) {
            throw new FileNotFoundException(resource);
        }
        FileUtils.copyURLToFile(url, file);
    }

    protected void copyExecutableResource(String resource, File file) throws IOException {
        copyResource(resource, file);
        file.setExecutable(true);
    }

    protected List<File> getDependencyFiles(List<String> scopes) throws MojoExecutionException {
        Set<File> directDeps = extractArtifactPaths(this.project.getDependencyArtifacts(), scopes);
        Set<File> transitiveDeps = extractArtifactPaths(this.project.getArtifacts(), scopes);
        return Stream.concat(directDeps.stream(), transitiveDeps.stream())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<File> extractArtifactPaths(Set<Artifact> artifacts, List<String> scopes) {
        return artifacts.stream()
                .filter(e -> scopes.contains(e.getScope()))
                .filter(e -> e.getType().equals("jar"))
                .map(this::asMavenCoordinates)
                .distinct()
                .map(this::resolveArtifact)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String asMavenCoordinates(Artifact artifact) {
        StringBuilder coords = new StringBuilder().append(artifact.getGroupId()).append(":")
                .append(artifact.getArtifactId());
        if (!"jar".equals(artifact.getType()) || artifact.hasClassifier()) {
            coords.append(":").append(artifact.getType());
        }
        if (artifact.hasClassifier()) {
            coords.append(":").append(artifact.getClassifier());
        }
        coords.append(":").append(artifact.getVersion());
        return coords.toString();
    }

    protected void unpackYamcsWeb(File unpackDirectory) throws MojoExecutionException {
        unpackDirectory.mkdirs();
        Set<Artifact> artifacts = (Set<Artifact>) this.project.getArtifacts();
        for (Artifact artifact : artifacts) {
            if (artifact.getArtifactId().equals("yamcs-web")) {
                try {
                    UnArchiver unArchiver = archiverManager.getUnArchiver(artifact.getType());
                    unArchiver.setOverwrite(true);
                    unArchiver.setSourceFile(artifact.getFile());
                    unArchiver.setFileMappers(new FileMapper[] { fileName -> fileName.replace("static/", ""), });
                    unArchiver.extract("static", unpackDirectory);
                } catch (NoSuchArchiverException e) {
                    throw new MojoExecutionException("Unknown archiver type", e);
                }
            }
        }
    }

    protected File resolveArtifact(String artifact) {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new DefaultArtifact(artifact));
        try {
            ArtifactResult artifactResult = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
            if (artifactResult.isResolved()) {
                getLog().debug("Resolved: " + artifactResult.getArtifact().getArtifactId());
                return artifactResult.getArtifact().getFile();
            } else {
                getLog().error("Unable to resolve: " + artifact);
            }
        } catch (ArtifactResolutionException e) {
            getLog().error("Unable to resolve: " + artifact);
        }

        return null;
    }
}
