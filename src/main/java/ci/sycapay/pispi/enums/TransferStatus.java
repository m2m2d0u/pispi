package ci.sycapay.pispi.enums;

public enum TransferStatus {
    /** Pre-confirmation state introduced by the two-phase mobile flow
     *  ({@code POST /transferts} → {@code PUT /transferts/{id}}). Nothing
     *  has been emitted to the AIP yet; the PACS.008 / PAIN.013 send happens
     *  only on confirm. Maps to remote-spec {@code statut=initie}. */
    INITIE,
    PEND, ACCC, ACSC, ACSP, RJCT, TMOT, ECHEC;

    /**
     * Statuts terminaux : une fois atteints, aucune transition n'est plus
     * autorisée. Toute mutation ultérieure (callback PACS.002 tardif, ADMI.002
     * en retard, race condition) doit être rejetée pour préserver l'intégrité
     * de la transaction.
     *
     * <ul>
     *   <li>{@code ACCC} — accepté définitivement par le payé</li>
     *   <li>{@code ACSC} — settlement complet (post-compensation)</li>
     *   <li>{@code ACSP} — settlement en cours / partiel (final côté flux)</li>
     *   <li>{@code RJCT} — rejeté par la contrepartie</li>
     *   <li>{@code TMOT} — timeout AIP</li>
     *   <li>{@code ECHEC} — erreur technique (ADMI.002, network, etc.)</li>
     * </ul>
     */
    public boolean isTerminal() {
        return this == ACCC || this == ACSC || this == ACSP
                || this == RJCT || this == TMOT || this == ECHEC;
    }
}
