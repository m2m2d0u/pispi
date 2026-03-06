package ci.sycapay.pispi.exception;

import lombok.Getter;

@Getter
public class PiSpiException extends RuntimeException {

    private final String errorCode;

    public PiSpiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PiSpiException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
