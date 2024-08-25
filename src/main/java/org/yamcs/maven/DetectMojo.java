package org.yamcs.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * Generates metadata for detected plugins of the attached project.
 */
@Mojo(name = "detect", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class DetectMojo extends AbstractProgramMojo {

    /**
     * Skip execution
     */
    @Parameter(property = "yamcs.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected File classesDirectory;

    @Component
    protected ArchiverManager archiverManager;

    @Component
    protected MavenProjectHelper projectHelper;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping execution");
            return;
        }

        var projectBuilder = new JavaProjectBuilder();

        // Some syntax (often in generated code) trips the QDox parser.
        // Ignore those errors. We only care to find Yamcs plugin classes.
        projectBuilder.setErrorHandler(e -> getLog().debug("Squelching QDox parse exception", e));

        // Add dependencies to QDox, so it can correctly establish the "isA" relation.
        // For example, a plugin could be extending org.yamcs.AbstractPlugin
        var urls = getDependencyFiles(Arrays.asList("compile", "provided", "system")).stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(f -> {
                    try {
                        return f.toURI().toURL();
                    } catch (MalformedURLException e) {
                        throw new AssertionError(e);
                    }
                })
                .collect(Collectors.toList());
        var classLoader = new URLClassLoader(urls.toArray(new URL[0]));
        projectBuilder.addClassLoader(classLoader);

        // Add also the current project to QDox
        for (var sourceRoot : project.getCompileSourceRoots()) {
            projectBuilder.addSourceTree(new File(sourceRoot));
        }

        var yamcsPluginClasses = new ArrayList<JavaClass>();
        for (var javaClass : projectBuilder.getClasses()) {
            if (javaClass.isInterface() || javaClass.isAbstract()) {
                continue;
            }

            if (javaClass.isA("org.yamcs.Plugin")) {
                getLog().debug("Found plugin " + javaClass);
                yamcsPluginClasses.add(javaClass);
            }
        }

        if (yamcsPluginClasses.isEmpty()) {
            getLog().debug("Found 0 Yamcs plugins");
            return;
        }

        var spiFile = new File(classesDirectory, "META-INF/services/org.yamcs.Plugin");
        spiFile.getParentFile().mkdirs();
        try (var writer = new FileWriter(spiFile)) {
            for (var yamcsPluginClass : yamcsPluginClasses) {
                writer.write(yamcsPluginClass.getFullyQualifiedName());
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + spiFile, e);
        }

        var metadataDir = new File(classesDirectory, "META-INF/yamcs");
        for (var yamcsPluginClass : yamcsPluginClasses) {
            var pluginResourcesDir = new File(metadataDir, yamcsPluginClass.getFullyQualifiedName());
            pluginResourcesDir.mkdirs();

            var props = new Properties();
            props.setProperty("name", project.getArtifactId());
            if (project.getDescription() != null) {
                props.setProperty("description", project.getDescription());
            } else if (project.getName() != null) {
                props.setProperty("description", project.getName());
            }
            props.setProperty("version", project.getVersion());
            props.setProperty("generated", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));

            var org = project.getOrganization();
            if (org != null) {
                if (org.getName() != null) {
                    props.setProperty("organization", org.getName());
                }
                if (org.getUrl() != null) {
                    props.setProperty("organizationUrl", org.getUrl());
                }
            }

            var propsFile = new File(pluginResourcesDir, "plugin.properties");
            try (var out = new FileOutputStream(propsFile)) {
                props.store(out, null);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write " + propsFile, e);
            }
        }
    }
}
