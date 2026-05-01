package ci.sycapay.pispi.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représentation API d'une facture mensuelle (BCEAO §4.14 — CAMT.086 reçu
 * via {@code POST /rapports/factures/reponses}).
 *
 * <p>Le payload BCEAO peut contenir plusieurs groupes (sender/receiver) avec
 * plusieurs factures par groupe — chaque ligne de facture est persistée comme
 * un {@code PiInvoice} distinct. Les détails de service (montant taxes, etc.)
 * vivent dans {@link #serviceLines} sous forme JSON pour préserver tous les
 * champs §4.14.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {

    private String msgId;
    private String groupeId;
    private String statementId;
    private String senderName;
    private String senderId;
    private String receiverName;
    private String receiverId;
    private String dateDebutFacture;
    private String dateFinFacture;
    private String deviseCompte;

    /** Lignes de facturation détaillées (JSON brut). */
    private String serviceLines;

    private String createdAt;
}
