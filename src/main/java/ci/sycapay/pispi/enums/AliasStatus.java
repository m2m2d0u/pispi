package ci.sycapay.pispi.enums;

public enum AliasStatus {
    PENDING,  // Initial status when request is sent to PI-RAC
    ACTIVE,   // Alias successfully created/active in PI-RAC
    DELETED,  // Alias deleted from PI-RAC
    FAILED    // Operation failed
}
