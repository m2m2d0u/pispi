package ci.sycapay.pispi.enums;

public enum TransferStatus {
    /** Pre-confirmation state introduced by the two-phase mobile flow
     *  ({@code POST /transferts} → {@code PUT /transferts/{id}}). Nothing
     *  has been emitted to the AIP yet; the PACS.008 / PAIN.013 send happens
     *  only on confirm. Maps to remote-spec {@code statut=initie}. */
    INITIE,
    PEND, ACCC, ACSC, ACSP, RJCT, TMOT, ECHEC,
    /**
     * {@code Returned} — terminal. Posé après réception d'un PACS.004 INBOUND
     * sur {@code POST /retour-fonds} suite à une demande d'annulation acceptée
     * par la contrepartie : les fonds ont été effectivement retournés. Le
     * code {@code raisonRetour} de la PACS.004 est stocké dans
     * {@code codeRaison} pour traçabilité.
     *
     * <p>Statut distinct de {@code RJCT} : {@code RJCT} = transfer rejeté
     * par l'AIP au settlement (jamais abouti) ; {@code RTND} = transfer
     * abouti puis retourné suite à une annulation acceptée.
     */
    RTND;

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
     *   <li>{@code RTND} — Returned, fonds retournés via PACS.004 INBOUND
     *       suite à une annulation acceptée</li>
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
                || this == RJCT || this == TMOT || this == ECHEC
                || this == RTND;
    }

    /**
     * Normalisation pour le stockage local : tout transfer accepté est
     * collapsé en {@code ACCC}, peu importe la nuance ISO 20022.
     *
     * <p>Contexte : la spec BCEAO impose que le PACS.002 OUTBOUND porte
     * {@code statutTransaction=ACSP} (settlement en cours), et l'AIP est
     * censée renvoyer un PACS.002 INBOUND ACCC ou ACSC une fois la
     * compensation finalisée. En pratique, certaines AIP n'envoient jamais
     * cette finalisation (notamment sur les flux internes participant) —
     * la ligne resterait alors bloquée en ACSP, ce qui est techniquement
     * non-terminal mais sémantiquement « réussie » pour le métier.
     *
     * <p>Cette méthode est utilisée pour stocker le statut local après
     * l'envoi/réception d'un PACS.002 d'acceptation. Le payload OUTBOUND
     * vers l'AIP continue à utiliser {@code ACSP} explicitement (BCEAO
     * l'impose), seul le miroir local est uniformisé.
     *
     * <ul>
     *   <li>{@code ACSP} → {@code ACCC} (uniformisation des transferts réussis)</li>
     *   <li>tout autre statut → renvoyé tel quel</li>
     * </ul>
     */
    public static TransferStatus normalizeSuccess(TransferStatus status) {
        return status == ACSP ? ACCC : status;
    }

    /**
     * {@code true} si le statut représente une acceptation côté AIP, qu'elle
     * soit intermédiaire ({@code ACSP}) ou finalisée ({@code ACCC|ACSC}).
     * Utilisé pour transitionner les RTP en {@code ACCEPTED} dès qu'on
     * voit un PACS.002 d'acceptation, sans attendre un éventuel ACCC tardif.
     */
    public boolean isAccepted() {
        return this == ACCC || this == ACSC || this == ACSP;
    }
}
