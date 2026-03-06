package ci.sycapay.pispi.exception;

public class DuplicateRequestException extends PiSpiException {

    public DuplicateRequestException(String msgId) {
        super("DUPLICATE_REQUEST", "Duplicate message ID: " + msgId);
    }
}
