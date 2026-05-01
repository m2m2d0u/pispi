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
     *   <li>{@code ACCC} — Accepted, Credit Settlement Completed (post-compensation côté payé)</li>
     *   <li>{@code ACSC} — Accepted Settlement Completed (post-compensation côté payeur)</li>
     *   <li>{@code RJCT} — rejeté par la contrepartie</li>
     *   <li>{@code TMOT} — timeout AIP</li>
     *   <li>{@code ECHEC} — erreur technique (ADMI.002, network, etc.)</li>
     * </ul>
     *
     * <p>{@code ACSP} (Accepted Settlement in Process) est explicitement
     * <strong>NON-terminal</strong> : c'est un état intermédiaire signifiant
     * « accepté côté récepteur, settlement à finaliser par l'AIP ». L'AIP
     * envoie ensuite un PACS.002 INBOUND ACCC/ACSC qui transitionne la ligne
     * vers le statut terminal final.
     */
    public boolean isTerminal() {
        return this == ACCC || this == ACSC
                || this == RJCT || this == TMOT || this == ECHEC;
    }
}
