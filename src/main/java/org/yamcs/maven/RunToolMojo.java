package org.yamcs.maven;

import java.io.File;
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
 * Runs a Yamcs-related tool as part of a Maven build.
 */
@Mojo(name = "run-tool", requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class RunToolMojo extends AbstractProgramMojo {

    /**
     * The directory where Yamcs is installed.
     */
    @Parameter(property = "yamcs.directory", defaultValue = "${project.build.directory}/yamcs")
    private File directory;

    /**
     * Arguments passed to the tool. Add each argument in a &lt;arg&gt; subelement.
     */
    @Parameter(property = "yamcs.args")
    private List<String> args;

    /**
     * Class name of the tool to execute.
     */
    @Parameter(property = "yamcs.tool", required = true)
    protected String tool;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected File classesDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if (!directory.exists()) {
            throw new MojoExecutionException("Cannot find directory " + directory);
        }

        runTool();
    }

    private void runTool() throws MojoExecutionException {
        JavaProcessBuilder b = new JavaProcessBuilder(getLog(), -1);
        b.addEnvironment("CLASSPATH", buildClasspath());
        b.setDirectory(directory);
        b.setArgs(getArgs());
        b.setWaitFor(true);

        try {
            b.start();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute", e);
        }
    }

    protected List<String> getArgs() throws MojoExecutionException {
        List<String> result = new ArrayList<>();
        result.add(tool);
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
