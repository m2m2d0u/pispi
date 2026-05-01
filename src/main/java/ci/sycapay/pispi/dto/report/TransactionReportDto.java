package ci.sycapay.pispi.dto.report;

import ci.sycapay.pispi.enums.IndicateurSolde;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Représentation API d'une page de relevé de transactions (BCEAO §4.11.2 —
 * CAMT.052 reçu via {@code POST /rapports/telechargements/reponses}).
 *
 * <p>Une demande CAMT.060 TRANS pour une période peut produire plusieurs
 * lignes (une par page) — corrélées par {@link #identifiantReleve}. Le
 * champ {@link #transactions} contient la liste des transactions de la page
 * sérialisée en JSON brut tel que reçu de l'AIP, pour préserver tous les
 * détails sans friction sur le schéma local.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionReportDto {

    private String msgId;

    /** Format BCEAO : {@code RECO + CodeMembre(6) + DateCompens(14)}, 24 chars. */
    private String identifiantReleve;

    private Integer pageCourante;
    private Boolean dernierePage;
    private String dateDebutCompense;
    private String dateFinCompense;
    private String codeMembreParticipant;
    private Integer nbreTotalTransaction;
    private BigDecimal montantTotalCompensation;
    private IndicateurSolde indicateurSolde;

    /** Liste des transactions de la page, JSON brut tel que livré par l'AIP. */
    private String transactions;

    private String createdAt;
}
