package ci.sycapay.pispi.enums;

/**
 * Codes raison utilisés dans les messages CAMT.029 (rejet d'une demande de
 * retour de fonds) et PACS.004 (retour de fonds effectif), per BCEAO §4.8.
 *
 * <p>Ce même enum est partagé pour la lisibilité applicative — BCEAO autorise
 * en pratique le même catalogue de codes sur les deux flux. Les schémas
 * BCEAO XSD (cf. {@code interface-participant-openapi.json}) :
 * <pre>
 *   RetourFonds.raisonRetour : AC06|AC07|FR01|MD06|BE01|RR04|CUST
 *   (CAMT.029 raison)        : AC04|ARDT|CUST + autres codes ci-dessus
 * </pre>
 *
 * <p>Codes auto-rejet PI (cf. règles §4.8.1 « Traitement du message camt.056 ») :
 *
 * <ul>
 *   <li>{@code ARDT} — AlreadyReturnedTransaction : le transfer a déjà été
 *       retourné. Le participant payé doit auto-rejeter le camt.056 sans
 *       notifier son client.</li>
 *   <li>{@code AC04} — ClosedAccountNumber : le compte du client payé est
 *       clôturé. Le participant payé auto-rejette le camt.056.</li>
 * </ul>
 *
 * <p>Codes décision client :
 *
 * <ul>
 *   <li>{@code CUST} — CustomerDecision : le client payé a décidé d'accepter
 *       (PACS.004 raisonRetour) ou de rejeter (CAMT.029 raison) la demande
 *       d'annulation reçue.</li>
 * </ul>
 *
 * <p>Codes complémentaires (§5.6 BCEAO-ANNEXES) :
 *
 * <ul>
 *   <li>{@code AC06} — BlockedAccount : compte bloqué</li>
 *   <li>{@code AC07} — ClosedCreditorAccountNumber : compte créditeur clôturé</li>
 *   <li>{@code FR01} — Fraud : retour suite à fraude</li>
 *   <li>{@code MD06} — RefundRequestByEndCustomer : retour demandé par le client final</li>
 *   <li>{@code BE01} — InconsistentWithEndCustomer : identification incohérente</li>
 *   <li>{@code RR04} — RegulatoryReason : raison réglementaire (sanctions ONU, etc.)</li>
 * </ul>
 */
public enum CodeRaisonRetourFonds {
    AC04, AC06, AC07, ARDT, BE01, CUST, FR01, MD06, RR04
}
