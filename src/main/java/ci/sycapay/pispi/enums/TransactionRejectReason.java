package ci.sycapay.pispi.enums;

/**
 * Codes raison autorisés pour {@code TransactionController::rejeter}
 * ({@code PUT /transferts/{id}/rejets}) — émission d'un PAIN.014 / PACS.002
 * de rejet vers l'AIP.
 *
 * <p>Liste BCEAO officielle (cf. BCEAO-ANNEXES.md §5.6 « CodeRaison ») :
 *
 * <ul>
 *   <li>{@code AB09} — ErrorCreditorAgent : transaction rejetée à cause
 *       d'une erreur chez le participant payé</li>
 *   <li>{@code AC03} — InvalidCreditorAccountNumber : le numéro de compte
 *       du payé est invalide</li>
 *   <li>{@code AC04} — ClosedAccountNumber : numéro de compte payeur clôturé</li>
 *   <li>{@code AC06} — BlockedAccount : le compte spécifié est bloqué</li>
 *   <li>{@code AC07} — ClosedCreditorAccountNumber : numéro de compte payé
 *       clôturé</li>
 *   <li>{@code AG01} — TransactionForbidden : transaction interdite sur ce
 *       type de compte</li>
 *   <li>{@code AM21} — LimitExceeded : le montant de la transaction dépasse
 *       les limites convenues entre la banque et le client</li>
 *   <li>{@code FR01} — Fraud : suspicion de fraude</li>
 * </ul>
 *
 * <p>Tout autre code envoyé en {@code raison} est rejeté par l'AIP avec un
 * ADMI.002 « raison invalide ».
 */
public enum TransactionRejectReason {
    AB09, AC03, AC04, AC06, AC07, AG01, AM21, FR01
}
