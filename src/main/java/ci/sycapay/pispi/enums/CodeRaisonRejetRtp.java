package ci.sycapay.pispi.enums;

/**
 * Accepted PAIN.014 rejection reason codes for an inbound RTP (PAIN.013),
 * per the BCEAO PI-AIP specification.
 *
 * <ul>
 *   <li>{@code AC04} — Compte clôturé</li>
 *   <li>{@code AC06} — Compte bloqué</li>
 *   <li>{@code AG03} — Type de transaction non supporté pour ce type de compte</li>
 *   <li>{@code AM02} — Montant spécifique non autorisé</li>
 *   <li>{@code AM09} — Montant reçu ne correspond pas au montant convenu ou attendu</li>
 *   <li>{@code AM14} — Montant dépasse le montant maximum autorisé</li>
 *   <li>{@code APAR} — Paiement demandé déjà effectué par le payeur</li>
 *   <li>{@code BE01} — Incohérence entre les informations du message et celles du client final</li>
 *   <li>{@code BE05} — Partie ayant initié le message non reconnue par le client final</li>
 *   <li>{@code FR01} — Suspicion de fraude</li>
 *   <li>{@code RR07} — Justificatif de la demande de paiement invalide</li>
 * </ul>
 */
public enum CodeRaisonRejetRtp {
    AC04, AC06, AG03, AM02, AM09, AM14, APAR, BE01, BE05, FR01, RR07
}
