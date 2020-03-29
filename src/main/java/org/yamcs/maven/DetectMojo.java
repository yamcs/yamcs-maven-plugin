package org.yamcs.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;

import org.apache.maven.model.Organization;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.manager.ArchiverManager;

/**
 * Generates metadata for detected plugins of the attached project.
 */
@Mojo(name = "detect", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class DetectMojo extends AbstractMojo {

    /**
     * Skip execution
     */
    @Parameter(property = "yamcs.skip", defaultValue = "false")
    protected boolean skip;


    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;
    
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected File classesDirectory;

    @Component
    protected ArchiverManager archiverManager;

    @Component
    protected MavenProjectHelper projectHelper;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping execution");
            return;
        }
        JavaProjectBuilder projectBuilder = new JavaProjectBuilder();
        for (String sourceRoot : project.getCompileSourceRoots()) {
            projectBuilder.addSourceTree(new File(sourceRoot));
        }

        List<JavaClass> yamcsPluginClasses = new ArrayList<>();
        for (JavaClass javaClass : projectBuilder.getClasses()) {
            if (javaClass.isInterface() || javaClass.isAbstract()) {
                continue;
            }

            for (JavaClass intf : javaClass.getInterfaces()) {
                if (intf.isA("org.yamcs.Plugin")) {
                    getLog().debug("Found plugin " + javaClass);
                    yamcsPluginClasses.add(javaClass);
                }
            }
        }

        if (yamcsPluginClasses.isEmpty()) {
            getLog().debug("Found 0 Yamcs plugins");
            return;
        }

        File spiFile = new File(classesDirectory, "META-INF/services/org.yamcs.Plugin");
        spiFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(spiFile)) {
            for (JavaClass yamcsPluginClass : yamcsPluginClasses) {
                writer.write(yamcsPluginClass.getFullyQualifiedName());
            }
            writer.write("\n");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + spiFile, e);
        }

        File metadataDir = new File(classesDirectory, "META-INF/yamcs");
        for (JavaClass yamcsPluginClass : yamcsPluginClasses) {
            File pluginResourcesDir = new File(metadataDir, yamcsPluginClass.getFullyQualifiedName());
            pluginResourcesDir.mkdirs();

            Properties props = new Properties();
            props.setProperty("name", project.getArtifactId());
            if (project.getDescription() != null) {
                props.setProperty("description", project.getDescription());
            }
            props.setProperty("version", project.getVersion());
            props.setProperty("generated", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));

            Organization org = project.getOrganization();
            if (org != null) {
                if (org.getName() != null) {
                    props.setProperty("organization", org.getName());
                }
                if (org.getUrl() != null) {
                    props.setProperty("organizationUrl", org.getUrl());
                }
            }

            File propsFile = new File(pluginResourcesDir, "plugin.properties");
            try (OutputStream out = new FileOutputStream(propsFile)) {
                props.store(out, null);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write " + propsFile, e);
            }
        }
    }
}
