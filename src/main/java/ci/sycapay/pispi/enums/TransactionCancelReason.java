package ci.sycapay.pispi.enums;

/**
 * Reason code for a transfer cancellation request ({@code pacs.028} camt.056),
 * per the BCEAO remote spec {@code TransactionCancelReason}.
 *
 * <ul>
 *   <li>{@code AC03} — Erreur sur le destinataire</li>
 *   <li>{@code AM09} — Erreur sur le montant</li>
 *   <li>{@code SVNR} — Service non rendu</li>
 *   <li>{@code DUPL} — Transaction déjà payée</li>
 *   <li>{@code FRAD} — Suspicion de fraude</li>
 * </ul>
 */
public enum TransactionCancelReason {
    AC03, AM09, SVNR, DUPL, FRAD
}
