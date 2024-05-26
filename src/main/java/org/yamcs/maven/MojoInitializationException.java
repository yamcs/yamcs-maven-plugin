package org.yamcs.maven;

/**
 * An exception to indicate that plugin initialization has failed.
 */
@SuppressWarnings("serial")
public final class MojoInitializationException extends RuntimeException {

    public MojoInitializationException(String message) {
        super(message);
    }

    public MojoInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
