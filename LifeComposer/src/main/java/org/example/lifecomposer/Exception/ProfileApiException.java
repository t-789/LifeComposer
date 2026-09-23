package org.example.lifecomposer.Exception;

/**
 * Profile-domain client error carrying a stable business code. The code is
 * mapped to an HTTP status only at the controller/advice boundary, so services
 * never depend on the web layer.
 */
public class ProfileApiException extends RuntimeException {

    private final String code;

    public ProfileApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
