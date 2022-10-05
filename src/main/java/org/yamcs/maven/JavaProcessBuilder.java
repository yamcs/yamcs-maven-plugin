package org.yamcs.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.StreamPumper;

public class JavaProcessBuilder {

    private static final String[] EXECUTABLE_EXTENSIONS = new String[] { "", ".sh", ".bash", ".exe", ".bat", ".cmd" };

    private List<String> args = new ArrayList<>();
    private List<String> jvmArgs = new ArrayList<>();
    private Map<String, String> extraEnv = new LinkedHashMap<>();
    private long stopTimeout;

    private Log log;

    private boolean waitFor = true;

    private File directory;

    private final File java = findJava();

    public JavaProcessBuilder(Log log, long stopTimeout) {
        this.log = log;
        this.stopTimeout = stopTimeout;
    }

    public Process start() throws Exception {
        ProcessBuilder pb = buildProcess();

        Process process = null;
        try {
            log.debug("Executing command: " + pb.command());
            process = pb.start();

            if (stopTimeout >= 0) {
                Process reference = process;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (reference.isAlive()) {
                        try {
                            boolean exited = reference.waitFor(stopTimeout, TimeUnit.MILLISECONDS);
                            if (!exited) {
                                System.out.println(String.format(
                                        "Yamcs did not stop in under %s milliseconds. Forcing...", stopTimeout));
                                // This is also no "guarantee", but we did our best.
                                reference.destroyForcibly();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }));
            }

            if (waitFor) {
                redirectOutput(process, log);
                process.waitFor();
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
            if (process.isAlive()) {
                process.destroy();
            }
            throw new Exception("Error running command: " + e.getMessage(), e);
        }
    }

    private ProcessBuilder buildProcess() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(java.getAbsolutePath());
        command.addAll(jvmArgs);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory);
        pb.environment().putAll(extraEnv);
        return pb;
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

    public JavaProcessBuilder addEnvironment(String key, String value) {
        extraEnv.put(key, value);
        return this;
    }

    public JavaProcessBuilder setDirectory(File directory) {
        if (!directory.isDirectory()) {
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

        if (found == null || !found.isFile()) {
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
