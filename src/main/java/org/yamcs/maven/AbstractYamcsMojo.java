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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Collects general Maven functionalities for use in specific Mojos
 */
public abstract class AbstractYamcsMojo extends AbstractProgramMojo {

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

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    protected File target;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected File classesDirectory;

    @Component
    protected ArchiverManager archiverManager;

    @Component
    protected MavenProjectHelper projectHelper;

    protected void initConfiguration(File directory) throws IOException {
        directory.mkdirs();

        File etcDir = new File(directory, "etc");
        etcDir.mkdir();

        if (configurationDirectory.exists()) {
            FileUtils.copyDirectoryStructure(configurationDirectory, directory);
        } else {
            getLog().warn(String.format("Yamcs configuration directory %s does not exist", configurationDirectory));
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
        return Stream.concat(directDeps.stream(), transitiveDeps.stream()).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
    }

    private Set<File> extractArtifactPaths(Set<Artifact> artifacts, List<String> scopes) {
        return artifacts.stream().filter(e -> scopes.contains(e.getScope())).filter(e -> e.getType().equals("jar"))
                .map(this::asMavenCoordinates).distinct().map(this::resolveArtifact)
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
