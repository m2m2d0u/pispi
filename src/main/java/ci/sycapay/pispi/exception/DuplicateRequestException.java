package ci.sycapay.pispi.exception;

public class DuplicateRequestException extends PiSpiException {

    public DuplicateRequestException(String msgId) {
        super("DUPLICATE_REQUEST", "Duplicate message ID: " + msgId);
    }

    public DuplicateRequestException(String resourceType, String identifier, String status) {
        super("DUPLICATE_" + resourceType.toUpperCase(),
              String.format("%s '%s' already exists with status %s", resourceType, identifier, status));
    }
}
