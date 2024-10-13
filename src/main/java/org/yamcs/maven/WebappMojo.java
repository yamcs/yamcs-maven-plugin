package org.yamcs.maven;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.StreamPumper;

/**
 * Mojo for compiling a Yamcs Web extension, integrated in a Maven build.
 * <p>
 * This is experimental, and undocumented.
 */
@Mojo(name = "webapp", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class WebappMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    @Parameter(required = false, property = "webapp.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(required = false, property = "webapp.skipInstall", defaultValue = "false")
    private boolean skipInstall;

    @Parameter(required = true, defaultValue = "${basedir}/src/main/webapp")
    private File webappSourceRoot;

    @Parameter(required = true, defaultValue = "${basedir}/src/main/webapp/dist")
    private File webappDistRoot;

    @Parameter(required = true, property = "webapp.outputDirectory", defaultValue = "${project.build.directory}/generated-resources/webapp")
    private File webappOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping mojo execution");
            return;
        }

        var resource = new Resource();
        resource.setDirectory(webappOutputDirectory.toString());
        getLog().debug("Add resource: " + resource.getDirectory());
        project.addResource(resource);

        if (!skipInstall) {
            if (session.isOffline()) {
                execNpm("install", "--offline");
            } else {
                execNpm("install");
            }
        }

        execNpm("run", "build");
        copyDist();
        generateManifest();
    }

    private void execNpm(String... args) throws MojoExecutionException {
        var pbArgs = new ArrayList<String>();
        pbArgs.add("npm");
        for (var arg : args) {
            pbArgs.add(arg);
        }

        var pb = new ProcessBuilder(pbArgs)
                .directory(webappSourceRoot)
                .redirectInput(Redirect.INHERIT);

        // Minimize noise
        pb.environment().put("NPM_CONFIG_AUDIT", "false"); // Equivalent of --no-audit
        pb.environment().put("NPM_CONFIG_FUND", "false"); // Equivalent of --ho-fund
        pb.environment().put("NPM_CONFIG_UPDATE_NOTIFIER", "false"); // Equivalent of --no-update-identifier

        getLog().info("Executing command: " + String.join(" ", pb.command()));

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute npm", e);
        }

        try {
            redirectOutput(process);
            var rc = process.waitFor();
            if (rc != 0) {
                throw new MojoExecutionException("npm did not exit successfully");
            }
        } catch (InterruptedException e) {
            if (process.isAlive()) {
                process.destroy();
            }
            Thread.currentThread().interrupt();
        }
    }

    private void copyDist() throws MojoExecutionException {
        var targetDir = new File(webappOutputDirectory, project.getArtifactId() + "-webapp");
        getLog().info("Copying " + webappOutputDirectory + " to " + targetDir);
        try {
            FileUtils.copyDirectory(webappDistRoot, targetDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy webapp dist", e);
        }
    }

    private void generateManifest() throws MojoExecutionException {
        var dist = webappOutputDirectory.toPath().resolve(project.getArtifactId() + "-webapp");
        try (var stream = Files.walk(dist)) {
            var manifest = stream.filter(Files::isRegularFile)
                    .map(p -> dist.relativize(p).toString())
                    .collect(Collectors.joining("\n"));

            var targetFile = dist.resolve("manifest.txt");
            getLog().info("Generating " + targetFile);
            Files.writeString(targetFile, manifest + "\n", UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list webapp dist files", e);
        }
    }

    private void redirectOutput(Process process) {
        var log = getLog();
        var outPumper = new StreamPumper(process.getInputStream(), log::info);
        var errPumper = new StreamPumper(process.getErrorStream(), log::error);

        outPumper.setPriority(Thread.MIN_PRIORITY + 1);
        errPumper.setPriority(Thread.MIN_PRIORITY + 1);

        outPumper.start();
        errPumper.start();
    }
}
