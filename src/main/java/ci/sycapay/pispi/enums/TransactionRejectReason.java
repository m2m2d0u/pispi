package ci.sycapay.pispi.enums;

/**
 * Reason code for rejecting a payment request (pain.014) or a cancellation request
 * (camt.029), per the BCEAO remote spec {@code TransactionRejectReason}.
 *
 * <ul>
 *   <li>{@code BE05} — La partie qui a initié le message n'est pas reconnue par le client final</li>
 *   <li>{@code AM09} — Le montant reçu ne correspond pas au montant convenu ou attendu</li>
 *   <li>{@code APAR} — Le paiement demandé a déjà été effectué par le payeur</li>
 *   <li>{@code RR07} — Justificatif de la demande de paiement invalide (ex. numéro de facture)</li>
 *   <li>{@code FR01} — Suspicion de fraude</li>
 *   <li>{@code CUST} — Décision du client (utilisé pour rejeter une demande d'annulation)</li>
 * </ul>
 */
public enum TransactionRejectReason {
    BE05, AM09, APAR, RR07, FR01, CUST
}
