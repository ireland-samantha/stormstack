package com.lightningfirefly.engine.rendering.testing;

/**
 * Exception thrown when an element cannot be found.
 * Mirrors Selenium's NoSuchElementException.
 */
public class NoSuchElementException extends RuntimeException {

    public NoSuchElementException(String message) {
        super(message);
    }

    public NoSuchElementException(String message, Throwable cause) {
        super(message, cause);
    }
}
