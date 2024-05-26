package org.yamcs.maven;

import static org.codehaus.plexus.util.StringUtils.join;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * This class represents an invokable configuration of the {@code protoc}
 * compiler. The actual executable is invoked
 * using the plexus {@link Commandline}.
 */
public class Protoc {

    /**
     * Prefix for logging the debug messages.
     */
    private static final String LOG_PREFIX = "[PROTOC] ";

    /**
     * Path to the {@code protoc} executable.
     */
    private final String executable;

    /**
     * A set of directories in which to search for definition imports.
     */
    private final List<File> protoPathElements;

    /**
     * A set of protobuf definitions to process.
     */
    private final List<File> protoFiles;

    /**
     * A directory into which Java source files will be generated.
     */
    private final File javaOutputDirectory;

    private final File pluginExecutable;

    private final File descriptorSetFile;

    private final boolean includeImportsInDescriptorSet;

    private final boolean includeSourceInfoInDescriptorSet;

    /**
     * A buffer to consume standard output from the {@code protoc} executable.
     */
    private final StringStreamConsumer output;

    /**
     * A buffer to consume error output from the {@code protoc} executable.
     */
    private final StringStreamConsumer error;

    /**
     * Constructs a new instance. This should only be used by the {@link Builder}.
     *
     * @param executable
     *                                         path to the {@code protoc}
     *                                         executable.
     * @param protoPath
     *                                         a set of directories in which to
     *                                         search for definition imports.
     * @param protoFiles
     *                                         a set of protobuf definitions to
     *                                         process.
     * @param javaOutputDirectory
     *                                         a directory into which Java source
     *                                         files will be generated.
     * @param descriptorSetFile
     *                                         The directory into which a descriptor
     *                                         set will be generated; if
     *                                         {@code null}, no descriptor set will
     *                                         be written
     * @param includeImportsInDescriptorSet
     *                                         If {@code true}, dependencies will be
     *                                         included in the descriptor set.
     * @param includeSourceInfoInDescriptorSet
     *                                         If {@code true}, source code
     *                                         information will be included in the
     *                                         descriptor set.
     * @param pluginExecutable
     *                                         location of protoc plugin executable
     * @param tempDirectory
     *                                         a directory where temporary files
     *                                         will be generated.
     */
    private Protoc(
            String executable,
            List<File> protoPath,
            List<File> protoFiles,
            File javaOutputDirectory,
            File descriptorSetFile,
            boolean includeImportsInDescriptorSet,
            boolean includeSourceInfoInDescriptorSet,
            File pluginExecutable) {
        if (executable == null) {
            throw new MojoConfigurationException("'executable' is null");
        }
        if (protoPath == null) {
            throw new MojoConfigurationException("'protoPath' is null");
        }
        if (protoFiles == null) {
            throw new MojoConfigurationException("'protoFiles' is null");
        }
        this.executable = executable;
        this.protoPathElements = protoPath;
        this.protoFiles = protoFiles;
        this.javaOutputDirectory = javaOutputDirectory;
        this.descriptorSetFile = descriptorSetFile;
        this.includeImportsInDescriptorSet = includeImportsInDescriptorSet;
        this.includeSourceInfoInDescriptorSet = includeSourceInfoInDescriptorSet;
        this.pluginExecutable = pluginExecutable;
        this.error = new StringStreamConsumer();
        this.output = new StringStreamConsumer();
    }

    /**
     * Invokes the {@code protoc} compiler using the configuration specified at
     * construction.
     *
     * @param log
     *            logger instance.
     * @return The exit status of {@code protoc}.
     * @throws CommandLineException
     *                              if command line environment cannot be set up.
     * @throws InterruptedException
     *                              if the execution was interrupted by the user.
     */
    public int execute(Log log) throws CommandLineException, InterruptedException {
        Commandline cl = new Commandline();
        cl.setExecutable(executable);
        String[] args = buildProtocCommand().toArray(new String[] {});
        cl.addArguments(args);

        return CommandLineUtils.executeCommandLine(cl, null, output, error);
    }

    /**
     * Creates the command line arguments.
     *
     * @return A list consisting of the executable followed by any arguments.
     */
    private List<String> buildProtocCommand() {
        List<String> command = new ArrayList<>();
        // add the executable
        for (File protoPathElement : protoPathElements) {
            command.add("--proto_path=" + protoPathElement);
        }
        if (javaOutputDirectory != null) {
            command.add("--java_out=" + javaOutputDirectory);
            command.add("--plugin=protoc-gen-yamcs=" + pluginExecutable);
            command.add("--yamcs_out=" + javaOutputDirectory);
        }
        for (File protoFile : protoFiles) {
            command.add(protoFile.toString());
        }
        if (descriptorSetFile != null) {
            command.add("--descriptor_set_out=" + descriptorSetFile);
            if (includeImportsInDescriptorSet) {
                command.add("--include_imports");
            }
            if (includeSourceInfoInDescriptorSet) {
                command.add("--include_source_info");
            }
        }
        return command;
    }

    /**
     * Logs execution parameters on debug level to the specified logger. All log
     * messages will be prefixed with
     * "{@value #LOG_PREFIX}".
     */
    public void logExecutionParameters(Log log) {
        if (log.isDebugEnabled()) {
            log.debug(LOG_PREFIX + "Executable: ");
            log.debug(LOG_PREFIX + ' ' + executable);

            if (protoPathElements != null && !protoPathElements.isEmpty()) {
                log.debug(LOG_PREFIX + "Protobuf import paths:");
                for (File protoPathElement : protoPathElements) {
                    log.debug(LOG_PREFIX + ' ' + protoPathElement);
                }
            }

            if (javaOutputDirectory != null) {
                log.debug(LOG_PREFIX + "Java output directory:");
                log.debug(LOG_PREFIX + ' ' + javaOutputDirectory);
            }

            if (pluginExecutable != null) {
                log.debug(LOG_PREFIX + "Plugin executable:");
                log.debug(LOG_PREFIX + ' ' + pluginExecutable);
            }

            if (descriptorSetFile != null) {
                log.debug(LOG_PREFIX + "Descriptor set output file:");
                log.debug(LOG_PREFIX + ' ' + descriptorSetFile);
                log.debug(LOG_PREFIX + "Include imports:");
                log.debug(LOG_PREFIX + ' ' + includeImportsInDescriptorSet);
            }

            log.debug(LOG_PREFIX + "Protobuf descriptors:");
            for (File protoFile : protoFiles) {
                log.debug(LOG_PREFIX + ' ' + protoFile);
            }

            List<String> cl = buildProtocCommand();
            if (cl != null && !cl.isEmpty()) {
                log.debug(LOG_PREFIX + "Command line options:");
                log.debug(LOG_PREFIX + join(cl.iterator(), " "));
            }
        }
    }

    /**
     * @return the output
     */
    public String getOutput() {
        return fixUnicodeOutput(output.getOutput());
    }

    /**
     * @return the error
     */
    public String getError() {
        return fixUnicodeOutput(error.getOutput());
    }

    /**
     * Transcodes the output from system default charset to UTF-8. Protoc emits
     * messages in UTF-8, but they are captured
     * into a stream that has a system-default encoding.
     *
     * @param message
     *                a UTF-8 message in system-default encoding.
     * @return the same message converted into a unicode string.
     */
    private static String fixUnicodeOutput(String message) {
        return new String(message.getBytes(), Charset.forName("UTF-8"));
    }

    /**
     * This class builds {@link Protoc} instances.
     */
    static final class Builder {

        /**
         * Path to the {@code protoc} executable.
         */
        private final String executable;

        private final LinkedHashSet<File> protopathElements;

        private final List<File> protoFiles;

        private File pluginExecutable;

        /**
         * A directory into which Java source files will be generated.
         */
        private File javaOutputDirectory;

        private File descriptorSetFile;

        private boolean includeImportsInDescriptorSet;

        private boolean includeSourceInfoInDescriptorSet;

        /**
         * Constructs a new builder.
         *
         * @param executable
         *                   The path to the {@code protoc} executable.
         */
        Builder(String executable) {
            this.executable = executable;
            protoFiles = new ArrayList<>();
            protopathElements = new LinkedHashSet<>();
        }

        /**
         * Sets the directory into which Java source files will be generated.
         *
         * @param javaOutputDirectory
         *                            a directory into which Java source files will be
         *                            generated.
         * @return this builder instance.
         */
        public Builder setJavaOutputDirectory(File javaOutputDirectory) {
            this.javaOutputDirectory = javaOutputDirectory;
            return this;
        }

        /**
         * Adds a proto file to be compiled. Proto files must be on the protopath and
         * this method will fail if a proto
         * file is added without first adding a parent directory to the protopath.
         *
         * @param protoFile
         *                  source protobuf definitions file.
         * @return The builder.
         */
        public Builder addProtoFile(File protoFile) {
            checkProtoFileIsInProtopath(protoFile);
            protoFiles.add(protoFile);
            return this;
        }

        public Builder setPluginExecutable(File pluginExecutable) {
            this.pluginExecutable = pluginExecutable;
            return this;
        }

        public Builder withDescriptorSetFile(
                File descriptorSetFile,
                boolean includeImports,
                boolean includeSourceInfoInDescriptorSet) {
            File descriptorSetFileParent = descriptorSetFile.getParentFile();
            if (!descriptorSetFileParent.exists()) {
                throw new MojoConfigurationException("Parent directory for 'descriptorSetFile' does not exist");
            }
            if (!descriptorSetFileParent.isDirectory()) {
                throw new MojoConfigurationException("Parent for 'descriptorSetFile' is not a directory");
            }
            this.descriptorSetFile = descriptorSetFile;
            this.includeImportsInDescriptorSet = includeImports;
            this.includeSourceInfoInDescriptorSet = includeSourceInfoInDescriptorSet;
            return this;
        }

        private void checkProtoFileIsInProtopath(File protoFile) {
            if (!protoFile.isFile()) {
                throw new MojoConfigurationException("Not a regular file: " + protoFile.getAbsolutePath());
            }
            if (!checkProtoFileIsInProtopathHelper(protoFile.getParentFile())) {
                throw new MojoConfigurationException("File is not in proto path: " + protoFile.getAbsolutePath());
            }
        }

        private boolean checkProtoFileIsInProtopathHelper(File directory) {
            if (!directory.isDirectory()) {
                throw new MojoConfigurationException("Not a directory: " + directory.getAbsolutePath());
            }
            if (protopathElements.contains(directory)) {
                return true;
            }
            File parentDirectory = directory.getParentFile();
            return parentDirectory != null && checkProtoFileIsInProtopathHelper(parentDirectory);
        }

        /**
         * Adds a collection of proto files to be compiled.
         *
         * @param protoFiles
         *                   a collection of source protobuf definition files.
         * @return this builder instance.
         * @see #addProtoFile(File)
         */
        public Builder addProtoFiles(Iterable<File> protoFiles) {
            for (File protoFile : protoFiles) {
                addProtoFile(protoFile);
            }
            return this;
        }

        /**
         * Adds the {@code protopathElement} to the protopath.
         *
         * @param protopathElement
         *                         A directory to be searched for imported protocol
         *                         buffer definitions.
         * @return The builder.
         */
        public Builder addProtoPathElement(File protopathElement) {
            if (protopathElement == null) {
                throw new MojoConfigurationException("'protopathElement' is null");
            }
            if (!protopathElement.isDirectory()) {
                throw new MojoConfigurationException(
                        "Proto path element is not a directory: " + protopathElement.getAbsolutePath());
            }
            protopathElements.add(protopathElement);
            return this;
        }

        /**
         * Adds a number of elements to the protopath.
         *
         * @param protopathElements
         *                          directories to be searched for imported protocol
         *                          buffer definitions.
         * @return this builder instance.
         * @see #addProtoPathElement(File)
         */
        public Builder addProtoPathElements(Iterable<File> protopathElements) {
            for (File protopathElement : protopathElements) {
                addProtoPathElement(protopathElement);
            }
            return this;
        }

        /**
         * Validates the internal state for consistency and completeness.
         */
        private void validateState() {
            if (protoFiles.isEmpty()) {
                throw new MojoConfigurationException("No proto files specified");
            }
            if (javaOutputDirectory == null) {
                throw new MojoConfigurationException("At least one of these properties must be set:" +
                        " 'javaOutputDirectory'");
            }
        }

        /**
         * Builds and returns a fully configured instance of {@link Protoc} wrapper.
         *
         * @return a configured {@link Protoc} instance.
         */
        public Protoc build() {
            validateState();
            return new Protoc(
                    executable,
                    new ArrayList<>(protopathElements),
                    protoFiles,
                    javaOutputDirectory,
                    descriptorSetFile,
                    includeImportsInDescriptorSet,
                    includeSourceInfoInDescriptorSet,
                    pluginExecutable);
        }
    }
}
