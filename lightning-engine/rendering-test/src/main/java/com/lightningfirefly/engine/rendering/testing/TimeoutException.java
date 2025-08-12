package com.lightningfirefly.engine.rendering.testing;

/**
 * Exception thrown when a wait times out.
 * Mirrors Selenium's TimeoutException.
 */
public class TimeoutException extends RuntimeException {

    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
