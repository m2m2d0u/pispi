package ci.sycapay.pispi.exception;

public class ResourceNotFoundException extends PiSpiException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType.toUpperCase() + "_NOT_FOUND", resourceType + " not found: " + identifier);
    }
}
