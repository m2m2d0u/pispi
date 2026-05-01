package ci.sycapay.pispi.enums;

/**
 * Reason code for a transfer cancellation request ({@code camt.056} —
 * {@code PUT /transferts/{id}/annulations}), per the BCEAO remote spec.
 *
 * <p>BCEAO XSD restriction (cf. {@code interface-participant-openapi.json}) :
 * <pre>{@code "raison": { "pattern": "AC03|FRAD|DUPL|AM09|SVNR" }}</pre>
 *
 * <p>Liste des raisons valides pour la demande d'annulation d'un transfer
 * (ordre conforme à la spec BCEAO) :
 *
 * <ul>
 *   <li>{@code AC03} — Erreur sur le destinataire (compte/alias incorrect)</li>
 *   <li>{@code FRAD} — Suspicion de fraude</li>
 *   <li>{@code DUPL} — Transaction déjà payée (doublon)</li>
 *   <li>{@code AM09} — Erreur sur le montant</li>
 *   <li>{@code SVNR} — Service non rendu</li>
 * </ul>
 *
 * <p>Tout autre code envoyé en {@code raison} est rejeté par l'AIP avec un
 * ADMI.002 « raison invalide ».
 */
public enum TransactionCancelReason {
    AC03, FRAD, DUPL, AM09, SVNR
}
