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

    /**
     * Time in milliseconds that a graceful stop of Yamcs is allowed to take. When
     * this time has passed, Yamcs is stopped forcefully.
     * 
     * A value &lt; 0 causes the stop to be done async from the Maven JVM.
     */
    @Parameter(property = "yamcs.stopTimeout")
    private long stopTimeout = 10000;

    @Override
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
        JavaProcessBuilder b = new JavaProcessBuilder(getLog(), stopTimeout);
        b.addEnvironment("CLASSPATH", buildClasspath());
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
        List<String> result = new ArrayList<>();
        result.add("-Djava.util.logging.manager=org.yamcs.logging.YamcsLogManager");
        // Linux/osx: "lib:lib/ext", windows: "lib;lib\ext"
        result.add("-Djava.library.path=lib" + File.pathSeparator + "lib" + File.separator + "ext");
        result.add("org.yamcs.YamcsServer");
        if (args != null) {
            for (String argsEl : args) {
                for (String arg : argsEl.split("\\s+")) {
                    if (!arg.trim().isEmpty()) {
                        result.add(arg);
                    }
                }
            }
        }
        return result;
    }

    protected List<String> getJvmArgs() {
        List<String> result = new ArrayList<>();
        if (jvmArgs != null) {
            for (String argsEl : jvmArgs) {
                for (String arg : argsEl.split("\\s+")) {
                    if (!arg.trim().isEmpty()) {
                        result.add(arg);
                    }
                }
            }
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
