package org.yamcs.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.Arg;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamPumper;

public class JavaProcessBuilder {

    private static final String[] EXECUTABLE_EXTENSIONS = new String[] {
        "", ".sh", ".bash", ".exe", ".bat", ".cmd"
    };

    private List<String> args = new ArrayList<>();
    private List<String> jvmArgs = new ArrayList<>();

    private Log log;

    private boolean waitFor = true;

    private File directory;    

    private final File java = findJava();

    public JavaProcessBuilder(Log log) {
        this.log = log;
    }

    public Process start() throws Exception {
        Commandline commandLine = buildCommandLine();

        Process process = null;
        try {
            log.debug("Executing command: " + commandLine);
            process = commandLine.execute();
            Process reference = process;
            Thread watchdog = new Thread(() -> {
                if (reference != null && reference.isAlive()) {
                    reference.destroy();
                }
            });

            Runtime.getRuntime().addShutdownHook(watchdog);

            if (waitFor) {
                redirectOutput(process, log);
                process.waitFor();
                if (!process.isAlive()) {
                    Runtime.getRuntime().removeShutdownHook(watchdog);
                }
            }

            return process;

        } catch (InterruptedException e) {
            if (process.isAlive()) {
                process.destroy();
            }
            // Be sure the interrupt flag is restored.
            Thread.currentThread().interrupt();
            return process;
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            throw new Exception("Error running command: " + e.getMessage(), e);
        }

    }

    private Commandline buildCommandLine() throws Exception {
        Commandline cli = new Commandline();

        // Disable explicit quoting of arguments
        cli.getShell().setQuotedArgumentsEnabled(false);
        cli.setExecutable(java.getAbsolutePath());
        cli.setWorkingDirectory(directory);

        jvmArgs.forEach(arg -> {
            Arg cliArg = cli.createArg();
            cliArg.setValue(arg);
        });
        args.forEach(arg -> {
            Arg cliArg = cli.createArg();
            cliArg.setValue(arg);
        });

        return cli;
    }

    public JavaProcessBuilder setArgs(List<String> argsList) {
        this.args = new ArrayList<>(argsList);
        return this;
    }

    public JavaProcessBuilder setWaitFor(boolean waitFor) {
        this.waitFor = waitFor;
        return this;
    }

    public JavaProcessBuilder setJvmOpts(List<String> jvmArgs) {
        if (jvmArgs == null) {
            this.jvmArgs = Collections.emptyList();
        } else {
            this.jvmArgs = jvmArgs;
        }
        return this;
    }

    public JavaProcessBuilder setDirectory(File directory) {
        if (! directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: '" + directory.getAbsolutePath() + "'");
        }
        this.directory = directory;
        return this;
    }

    private void redirectOutput(Process process, Log logger) {
        StreamPumper outPumper = new StreamPumper(process.getInputStream(), System.out::println);
        StreamPumper errPumper = new StreamPumper(process.getErrorStream(), System.err::println);

        outPumper.setPriority(Thread.MIN_PRIORITY + 1);
        errPumper.setPriority(Thread.MIN_PRIORITY + 1);

        outPumper.start();
        errPumper.start();
    }

    protected File findJava() {
        String javaHome = System.getProperty("java.home");
        File found;
        if (javaHome == null) {
            found = findExecutableInSystemPath("java");
        } else {
            File bin = new File(javaHome, "bin");
            found = find("java", bin);
        }

        if (found == null || ! found.isFile()) {
            throw new IllegalStateException("Unable to find the java executable in JAVA_HOME or in the system path");
        }
        return found;
    }

    public static File findExecutableInSystemPath(String executable) {
        String systemPath = System.getenv("PATH");
        if (systemPath == null) {
            return null;
        }

        String[] pathDirs = systemPath.split(File.pathSeparator);

        for (String pathDir : pathDirs) {
            File dir = new File(pathDir);
            if (dir.isDirectory()) {
                File file = findExecutableInDirectory(executable, dir);
                if (file != null) {
                    return file;
                }
            }
        }

        return null;
    }

    private static File findExecutableInDirectory(String executable, File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        for (String extension : EXECUTABLE_EXTENSIONS) {
            File file = new File(directory, executable + extension);
            if (file.isFile() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    private static File find(String executable, File... dirs) {
        if (dirs != null) {
            for (File hint : dirs) {
                File file = findExecutableInDirectory(executable, hint);
                if (file != null) {
                    return file;
                }
            }
        }
        return findExecutableInSystemPath(executable);
    }
}
