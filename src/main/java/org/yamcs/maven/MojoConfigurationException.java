package org.yamcs.maven;

/**
 * An exception to indicate that plugin configuration was incorrect.
 */
@SuppressWarnings("serial")
public final class MojoConfigurationException extends RuntimeException {

    public MojoConfigurationException(String message) {
        super(message);
    }

    public MojoConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
