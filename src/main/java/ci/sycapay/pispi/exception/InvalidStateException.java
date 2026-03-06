package ci.sycapay.pispi.exception;

public class InvalidStateException extends PiSpiException {

    public InvalidStateException(String message) {
        super("INVALID_STATE", message);
    }
}
