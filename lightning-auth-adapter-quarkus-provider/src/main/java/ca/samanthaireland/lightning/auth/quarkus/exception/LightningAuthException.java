package ca.samanthaireland.lightning.auth.quarkus.exception;

/**
 * Base exception for Lightning Auth adapter errors.
 */
public class LightningAuthException extends RuntimeException {

    private final String code;

    public LightningAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LightningAuthException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
