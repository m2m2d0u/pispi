package ci.sycapay.pispi.dto.report;

import ci.sycapay.pispi.enums.TypeRapport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse aux endpoints d'envoi de demande de rapport (CAMT.060) — porte le
 * {@code msgId} généré localement pour permettre au backend de tracer la
 * demande, corréler le futur callback ({@code ADMI.004 RECO} pour TRANS,
 * {@code CAMT.053} direct pour COMP, {@code CAMT.086} pour FACT) et afficher
 * un statut « en attente » à l'utilisateur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequestResponse {

    /** msgId BCEAO du CAMT.060 émis. Format {@code M{codeMembre}{28-chars}}. */
    private String msgId;

    /** Type de rapport demandé : {@code COMP|TRANS|FACT}. */
    private TypeRapport typeRapport;

    /** Date de début de période transmise à l'AIP, format {@code YYYY-MM-DD}. */
    private String dateDebutPeriode;

    /** Heure de début de période transmise à l'AIP, format BCEAO {@code HH:mm:ss.SSSZ}. */
    private String heureDebutPeriode;
}
