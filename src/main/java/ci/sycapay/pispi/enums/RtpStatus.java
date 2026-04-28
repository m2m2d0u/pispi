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
    /** Échue parce que la {@code dateHeureLimiteAction} est passée sans réponse. */
    EXPIRED,
    /** Timeout AIP — pas de réponse dans les délais. */
    TMOT;

    /**
     * Statuts terminaux : une fois atteints, plus aucune transition n'est
     * autorisée. Empêche par exemple un PACS.002 tardif de transiter un RTP
     * déjà ACCEPTED en RJCT, ou un PAIN.014 retardataire de cogner un RTP
     * déjà EXPIRED.
     */
    public boolean isTerminal() {
        return this == ACCEPTED || this == RJCT
                || this == EXPIRED || this == TMOT;
    }
}
