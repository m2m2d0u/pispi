package ci.sycapay.pispi.exception;

public class AipCommunicationException extends PiSpiException {

    public AipCommunicationException(String message) {
        super("AIP_UNAVAILABLE", message);
    }

    public AipCommunicationException(String message, Throwable cause) {
        super("AIP_UNAVAILABLE", message, cause);
    }
}
