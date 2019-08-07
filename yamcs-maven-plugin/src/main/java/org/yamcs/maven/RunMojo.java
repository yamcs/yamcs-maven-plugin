package org.yamcs.maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Runs Yamcs as part of a Maven build.
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class RunMojo extends AbstractYamcsMojo {

    /**
     * The directory to create the runtime Yamcs server configuration under.
     */
    @Parameter(property = "yamcs.directory", defaultValue = "${project.build.directory}/yamcs")
    private File directory;

    /**
     * JVM Arguments passed to the forked JVM that runs Yamcs.
     * Add each argument in a &lt;jvmArg&gt; subelement.
     */
    @Parameter(property = "yamcs.jvmArgs")
    private List<String> jvmArgs;

    /**
     * Arguments passed to the Yamcs executable. Add each argument in a &lt;arg&gt;
     * subelement.
     */
    @Parameter(property = "yamcs.args")
    private List<String> args;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping execution");
            return;
        }

        try {
            getLog().info("Creating configuration at " + directory);
            initConfiguration(directory);
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create configuration", e);
        }

        runYamcs();
    }

    private void runYamcs() throws MojoExecutionException {
        JavaProcessBuilder b = new JavaProcessBuilder(getLog());
        b.setDirectory(directory);
        b.setArgs(getArgs());
        b.setJvmOpts(getJvmArgs());
        b.setWaitFor(true);

        try {
            b.start();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute", e);
        }
    }

    protected List<String> getArgs() throws MojoExecutionException {
        // YConfiguration expects config files from the classpath
        String classpath = new File(directory, "etc").getAbsolutePath();
        classpath += File.pathSeparator + buildClasspath();

        List<String> result = new ArrayList<>();
        result.add("-cp");
        result.add(classpath);
        result.add("org.yamcs.YamcsServer");
        if (args != null) {
            result.addAll(args);
        }
        return result;
    }

    protected List<String> getJvmArgs() {
        List<String> result = new ArrayList<>();
        if (jvmArgs != null) {
            result.addAll(jvmArgs);
        }
        return result;
    }

    /**
     * Returns a classpath string representing all jars from maven dependencies and
     * target/classes of the current project.
     */
    protected String buildClasspath() throws MojoExecutionException {
        List<String> classpathEntries = new ArrayList<>();
        classpathEntries.add(classesDirectory.toString());

        List<String> scopes = Arrays.asList("compile", "runtime", "provided");
        List<File> dependencyFiles = getDependencyFiles(scopes);
        classpathEntries.addAll(dependencyFiles.stream().map(File::toString).collect(Collectors.toList()));

        String classpath = String.join(File.pathSeparator, classpathEntries);
        getLog().debug("Classpath: " + classpath);
        return classpath.toString();
    }
}
