package ci.sycapay.pispi.controller.outbound;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.dto.report.CompensationDto;
import ci.sycapay.pispi.dto.report.GuaranteeDto;
import ci.sycapay.pispi.dto.report.InvoiceDto;
import ci.sycapay.pispi.dto.report.ReportDownloadRequest;
import ci.sycapay.pispi.dto.report.ReportRequest;
import ci.sycapay.pispi.dto.report.ReportRequestResponse;
import ci.sycapay.pispi.dto.report.TransactionReportDto;
import ci.sycapay.pispi.enums.TypeRapport;
import ci.sycapay.pispi.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints backend pour la chaîne BCEAO §4.10–§4.14 (rapports de
 * compensation, relevés de transactions, factures, garanties).
 *
 * <p>Architecture asynchrone : chaque {@code POST} émet un CAMT.060 vers
 * l'AIP et retourne immédiatement avec le {@code msgId} émis. Les rapports
 * arrivent ensuite via callbacks (cf. {@code ReportCallbackController}) :
 *
 * <ul>
 *   <li>{@code COMP} → CAMT.053 push direct sur {@code /reglements/soldes}
 *       → exposé en lecture via {@code GET /api/v1/reports/compensation}</li>
 *   <li>{@code TRANS} → ADMI.004 INFO {@code RECO...} → auto-download (cf.
 *       {@code NotificationCallbackController}) → CAMT.052 sur
 *       {@code /rapports/telechargements/reponses} → exposé via
 *       {@code GET /api/v1/reports/transactions}</li>
 *   <li>{@code FACT} → CAMT.086 sur {@code /rapports/factures/reponses}
 *       → exposé via {@code GET /api/v1/reports/invoices}</li>
 *   <li>Garantie → CAMT.010 push direct + REDA.017 sur changement sponsor
 *       → exposé via {@code GET /api/v1/reports/guarantee}</li>
 * </ul>
 */
@Tag(name = "Reports")
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    // -----------------------------------------------------------------------
    // Demandes OUTBOUND — émission CAMT.060
    // -----------------------------------------------------------------------

    @Operation(summary = "Demander un solde de compensation (CAMT.060 COMP)",
            description = "Émet un CAMT.060 typeRapport=COMP. La réponse arrive en async "
                    + "via /reglements/soldes (CAMT.053). Retourne le msgId pour traçabilité.")
    @PostMapping("/compensation")
    public ResponseEntity<ApiResponse<ReportRequestResponse>> requestCompensation(
            @Valid @RequestBody ReportRequest request) {
        ReportRequestResponse data = service.requestReport(TypeRapport.COMP, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Demander le détail des transactions (CAMT.060 TRANS)",
            description = "Émet un CAMT.060 typeRapport=TRANS pour la période donnée. La PI "
                    + "renvoie d'abord un ADMI.004 INFO avec un identifiant RECO..., qui "
                    + "déclenche automatiquement le téléchargement et l'arrivée du CAMT.052. "
                    + "Retourne le msgId pour traçabilité.")
    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<ReportRequestResponse>> requestTransactions(
            @Valid @RequestBody ReportRequest request) {
        ReportRequestResponse data = service.requestReport(TypeRapport.TRANS, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Demander la facture mensuelle (CAMT.060 FACT)",
            description = "Émet un CAMT.060 typeRapport=FACT pour la période mensuelle "
                    + "donnée. La réponse arrive via /rapports/factures/reponses (CAMT.086). "
                    + "Retourne le msgId pour traçabilité.")
    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<ReportRequestResponse>> requestInvoices(
            @Valid @RequestBody ReportRequest request) {
        ReportRequestResponse data = service.requestReport(TypeRapport.FACT, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.accepted(data));
    }

    @Operation(summary = "Re-télécharger un rapport par ID (manuel)",
            description = "Émet manuellement la requête /rapports/telechargements pour "
                    + "récupérer un rapport déjà préparé côté AIP, identifié par son "
                    + "RECO/COMP-id. Normalement déclenché automatiquement par le callback "
                    + "ADMI.004 ; cet endpoint est un fallback en cas d'échec auto-download "
                    + "ou pour récupérer un rapport ancien.")
    @PostMapping("/download")
    public ResponseEntity<ApiResponse<Map<String, Object>>> downloadReport(
            @Valid @RequestBody ReportDownloadRequest request) {
        Map<String, Object> result = service.downloadReport(request.getId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // -----------------------------------------------------------------------
    // Listing INBOUND — accès aux données reçues via callbacks
    // -----------------------------------------------------------------------

    @Operation(summary = "Lister les soldes de compensation (CAMT.053)",
            description = "Page paginée des soldes reçus via /reglements/soldes, triés "
                    + "par date de réception décroissante.")
    @GetMapping("/compensation")
    public ApiResponse<Page<CompensationDto>> listCompensation(Pageable pageable) {
        return ApiResponse.ok(service.listCompensations(pageable));
    }

    @Operation(summary = "Lister les relevés de transactions (CAMT.052)",
            description = "Page paginée des relevés reçus via "
                    + "/rapports/telechargements/reponses, triés par date de réception "
                    + "décroissante. Une demande TRANS peut produire plusieurs lignes "
                    + "(une par page) corrélées par identifiantReleve — voir l'endpoint "
                    + "GET /transactions/{identifiantReleve} pour récupérer toutes les "
                    + "pages d'un relevé donné.")
    @GetMapping("/transactions")
    public ApiResponse<Page<TransactionReportDto>> listTransactionReports(Pageable pageable) {
        return ApiResponse.ok(service.listTransactionReports(pageable));
    }

    @Operation(summary = "Récupérer toutes les pages d'un relevé identifié",
            description = "Renvoie les pages d'un relevé de transactions (CAMT.052) "
                    + "corrélées par leur identifiantReleve (format BCEAO "
                    + "RECO+codeMembre+dateCompens), triées par pageCourante ascendant.")
    @GetMapping("/transactions/{identifiantReleve}")
    public ApiResponse<Page<TransactionReportDto>> getTransactionReportPages(
            @Parameter(description = "Identifiant BCEAO du relevé (RECO + codeMembre + dateCompens)")
            @PathVariable String identifiantReleve,
            Pageable pageable) {
        return ApiResponse.ok(service.listTransactionReportPages(identifiantReleve, pageable));
    }

    @Operation(summary = "Lister les factures mensuelles (CAMT.086)",
            description = "Page paginée des factures reçues via "
                    + "/rapports/factures/reponses, triées par date de réception décroissante.")
    @GetMapping("/invoices")
    public ApiResponse<Page<InvoiceDto>> listInvoices(Pageable pageable) {
        return ApiResponse.ok(service.listInvoices(pageable));
    }

    @Operation(summary = "État courant des garanties",
            description = "Renvoie la dernière mise à jour de garantie reçue (CAMT.010 "
                    + "modification directe ou REDA.017 changement sponsor), reflétant "
                    + "la position de collateral courante du participant.")
    @GetMapping("/guarantee")
    public ApiResponse<GuaranteeDto> getGuarantee() {
        return ApiResponse.ok(service.getLatestGuarantee());
    }
}
