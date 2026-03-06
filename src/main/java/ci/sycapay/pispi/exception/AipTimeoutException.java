package ci.sycapay.pispi.exception;

public class AipTimeoutException extends PiSpiException {

    public AipTimeoutException(String message) {
        super("AIP_TIMEOUT", message);
    }
}
