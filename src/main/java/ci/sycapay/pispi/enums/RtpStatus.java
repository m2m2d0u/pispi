package ci.sycapay.pispi.enums;

public enum RtpStatus {
    /** Inbound RTP received, awaiting the payer's decision. */
    PENDING,
    /** Payer confirmed acceptance — PACS.008 sent, waiting for AIP PACS.002. */
    PREVALIDATION,
    /** AIP confirmed the transfer irrevocably (ACCC / ACSC). */
    ACCEPTED,
    /** Rejected — either by the payer (PAIN.014) or by the AIP (PACS.002 RJCT). */
    RJCT,
    EXPIRED,
    TMOT
}
