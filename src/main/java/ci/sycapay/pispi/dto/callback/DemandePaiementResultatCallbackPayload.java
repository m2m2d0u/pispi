package ci.sycapay.pispi.dto.callback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Inbound Request-to-Pay rejection result payload
 * (PAIN.014 — schéma BCEAO {@code ReponseDemandePaiement}).
 * Poussé par l'AIP pour notifier ce PI d'un rejet émis par un payeur sur une
 * demande initiée par ce PI.
 */
@Data
@Schema(description = "Inbound PAIN.014 — BCEAO ReponseDemandePaiement schema")
public class DemandePaiementResultatCallbackPayload {

    @Schema(description = "Identifiant du message de réponse.",
            example = "MCIE002XJWRES01400000000000000001")
    private String msgId;

    @Schema(description = "Identifiant du message PAIN.013 d'origine (format AIP).",
            example = "MPIUMOAW8Os2wIHBS2j1XngvTOc0b6Ixffm")
    private String msgIdDemande;

    @Schema(description = "Identifiant métier de la demande de paiement.",
            example = "RTP-INV-20260422-001")
    private String identifiantDemandePaiement;

    @Schema(description = "Référence de lot (optionnel, repris de la demande).")
    private String referenceBulk;

    @Schema(description = "Identifiant bout-en-bout (repris de la demande).",
            example = "ECIE001XJWE2E0001420260422000001")
    private String endToEndId;

    /**
     * BCEAO attribut spec: pattern = {@code RJCT}. Le seul statut émis par PAIN.014
     * côté AIP est le rejet ; une acceptation déclenche directement un PACS.008.
     */
    @Schema(description = "Statut final (RJCT — seul statut supporté par PAIN.014).",
            example = "RJCT", allowableValues = {"RJCT"})
    private String statut;

    @Schema(description = "Code ISO de rejet. Pattern BCEAO: [A-Z]{2}\\d{2} "
            + "(ex. AC01, AC04, AC06, AM04, AM09, CUST).",
            example = "AM09")
    private String codeRaison;
}
