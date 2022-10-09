package org.yamcs.maven;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public abstract class AbstractProgramMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Component
    protected RepositorySystem repositorySystem;

    protected List<File> getDependencyFiles(List<String> scopes) throws MojoExecutionException {
        return extractArtifactPaths(project.getArtifacts(), scopes).stream()
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
