package org.yamcs.maven;

import java.util.List;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Runs Yamcs in debug mode as part of a Maven build.
 */
@Mojo(name = "debug", requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class DebugMojo extends RunMojo {

    /**
     * Port for debugging
     */
    @Parameter(property = "yamcs.jvm.debug.port", defaultValue = "7896")
    protected int jvmDebugPort;

    /**
     * Suspend when debugging
     */
    @Parameter(property = "yamcs.jvm.debug.suspend")
    protected boolean jvmDebugSuspend = false;

    @Override
    protected List<String> getJvmArgs() {
        List<String> args = super.getJvmArgs();
        args.add("-Xdebug");
        args.add("-Xrunjdwp:transport=dt_socket" + 
                ",address=" + String.valueOf(jvmDebugPort) +
                ",suspend=" + (jvmDebugSuspend ? "y" : "n") +
                ",server=y");
        return args;
    }
}
