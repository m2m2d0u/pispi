package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.entity.PiCompensation;
import ci.sycapay.pispi.entity.PiGuarantee;
import ci.sycapay.pispi.entity.PiInvoice;
import ci.sycapay.pispi.entity.PiTransactionReport;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.*;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import ci.sycapay.pispi.dto.callback.CompensationCallbackPayload;
import ci.sycapay.pispi.dto.callback.FactureCallbackPayload;
import ci.sycapay.pispi.dto.callback.GarantieCallbackPayload;
import ci.sycapay.pispi.dto.callback.ReleveCallbackPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDate;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "Report Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportCallbackController {

    private final MessageLogService messageLogService;
    private final PiTransactionReportRepository transactionReportRepository;
    private final PiCompensationRepository compensationRepository;
    private final PiGuaranteeRepository guaranteeRepository;
    private final PiInvoiceRepository invoiceRepository;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Receive transaction statement (CAMT.052)",
            description = "Called by the AIP to deliver the transaction statement requested "
                    + "via POST /api/v1/reports/transactions (puis téléchargé via "
                    + "/rapports/telechargements). Persiste les 11 champs BCEAO §4.11.2 et "
                    + "fire un TRANSACTION_REPORT webhook.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ReleveCallbackPayload.class)))
    @PostMapping("/rapports/telechargements/reponses")
    public ResponseEntity<Void> receiveTransactionReport(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.052 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, null, IsoMessageType.CAMT_052, MessageDirection.INBOUND, payload, 202, null);

        try {
            // BCEAO §4.11.2 — les champs numériques/booléens peuvent arriver
            // comme strings (cf. exemple spec p.168 où "pageCourante":"1" et
            // "dernierePage":"true" sont quotés). Parsing défensif via
            // toInt/toBool/toBigDecimal pour absorber les deux formats.
            PiTransactionReport report = PiTransactionReport.builder()
                    .msgId(msgId)
                    .identifiantReleve((String) payload.get("identifiantReleve"))
                    .pageCourante(toInt(payload.get("pageCourante")))
                    .dernierePage(toBool(payload.get("dernierePage")))
                    .dateDebutCompense(parseDateTime(payload.get("dateDebutCompense")))
                    .dateFinCompense(parseDateTime(payload.get("dateFinCompense")))
                    .codeMembreParticipant((String) payload.get("codeMembreParticipant"))
                    .nbreTotalTransaction(toInt(payload.get("nbreTotalTransaction")))
                    .montantTotalCompensation(toBigDecimal(payload.get("montantTotalCompensation")))
                    .indicateurSolde(toIndicateurSolde(payload.get("indicateurSolde")))
                    .transactions(objectMapper.writeValueAsString(payload.get("transactions")))
                    .build();
            transactionReportRepository.save(report);
        } catch (Exception e) {
            log.error("Failed to persist CAMT.052 [msgId={}]: {}", msgId, e.getMessage(), e);
        }

        webhookService.notify(WebhookEventType.TRANSACTION_REPORT, null, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    // =======================================================================
    // Defensive parsers — BCEAO emits numeric/boolean fields as strings on
    // some endpoints (cf. CAMT.052 §4.11.2 example) ; on supporte les deux
    // formats sans planter le callback.
    // =======================================================================

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Boolean toBool(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString().trim());
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static IndicateurSolde toIndicateurSolde(Object v) {
        if (v == null) return null;
        try { return IndicateurSolde.valueOf(v.toString().trim()); }
        catch (IllegalArgumentException e) { return null; }
    }

    @Operation(summary = "Receive compensation report (CAMT.053)", description = "Called by the AIP to deliver clearing/settlement balances requested via POST /api/v1/reports/compensation. Each balance entry is persisted as a PiCompensation record.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CompensationCallbackPayload.class)))
    @PostMapping("/reglements/soldes")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> receiveCompensation(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.053 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, null, IsoMessageType.CAMT_053, MessageDirection.INBOUND, payload, 202, null);

        // BCEAO §4.12 — toutes les valeurs numériques arrivent comme strings
        // dans l'exemple spec ("montant":"10000000"). Parsing défensif aussi
        // sur balanceType et operationType pour ne pas planter le callback
        // si la PI introduit un nouveau code.
        List<Map<String, Object>> soldes = (List<Map<String, Object>>) payload.get("soldes");
        if (soldes != null) {
            for (Map<String, Object> solde : soldes) {
                PiCompensation compensation = PiCompensation.builder()
                        .msgId(msgId)
                        .soldeId((String) solde.get("id"))
                        .dateDebutCompense(parseDateTime(solde.get("dateDebutCompense")))
                        .dateFinCompense(parseDateTime(solde.get("dateFinCompense")))
                        .participant((String) solde.get("participant"))
                        .participantSponsor((String) solde.get("participantSponsor"))
                        .balanceType(toTypeBalanceCompense(solde.get("balanceType")))
                        .montant(toBigDecimal(solde.get("montant")))
                        .operationType(toIndicateurSolde(solde.get("operationType")))
                        .dateBalance(parseDateTime(solde.get("dateBalance")))
                        .build();
                compensationRepository.save(compensation);
            }
        }

        webhookService.notify(WebhookEventType.COMPENSATION_REPORT, null, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive guarantee update (CAMT.010)",
            description = "Called by the AIP to push an update to this participant's guarantee "
                    + "(collateral) position. Persiste les champs structurés BCEAO §4.13 ; les "
                    + "champs conditionnels relatifs au solde de compensation "
                    + "(montantSoldeCompense, montantMobilise, typeOperationSolde…) restent "
                    + "disponibles dans la colonne payload JSON. Fires un GUARANTEE_UPDATED "
                    + "webhook avec le payload brut.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = GarantieCallbackPayload.class)))
    @PostMapping("/notifications/garantie")
    public ResponseEntity<Void> receiveGuarantee(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.010 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, null, IsoMessageType.CAMT_010, MessageDirection.INBOUND, payload, 202, null);

        try {
            // BCEAO §4.13 — la clé est `dateEffectiveOperationGarantie` (forme
            // longue) côté payload AIP. On stocke dans la colonne historique
            // `dateEffectiveGarantie` (forme courte). Fallback sur la forme
            // courte au cas où un payload de test utiliserait l'ancien nom.
            Object dateEffectiveRaw = payload.get("dateEffectiveOperationGarantie");
            if (dateEffectiveRaw == null) dateEffectiveRaw = payload.get("dateEffectiveGarantie");

            PiGuarantee guarantee = PiGuarantee.builder()
                    .msgId(msgId)
                    .sourceMessageType(IsoMessageType.CAMT_010)
                    .participantSponsor((String) payload.get("participantSponsor"))
                    .montantGarantie(toBigDecimal(payload.get("montantGarantie")))
                    .montantRestantGarantie(toBigDecimal(payload.get("montantRestantGarantie")))
                    .typeOperationGarantie(toTypeOperationGarantie(payload.get("typeOperationGarantie")))
                    .dateEffectiveGarantie(parseDateTime(dateEffectiveRaw))
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            guaranteeRepository.save(guarantee);
        } catch (Exception e) {
            log.error("Failed to persist CAMT.010 [msgId={}]: {}", msgId, e.getMessage(), e);
        }

        webhookService.notify(WebhookEventType.GUARANTEE_UPDATED, null, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    private static TypeOperationGarantie toTypeOperationGarantie(Object v) {
        if (v == null) return null;
        try { return TypeOperationGarantie.valueOf(v.toString().trim()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static TypeBalanceCompense toTypeBalanceCompense(Object v) {
        if (v == null) return null;
        try { return TypeBalanceCompense.valueOf(v.toString().trim()); }
        catch (IllegalArgumentException e) { return null; }
    }

    @Operation(summary = "Receive invoice report (CAMT.086)", description = "Called by the AIP to deliver the billing invoice requested via POST /api/v1/reports/invoices. Each invoice line item is persisted as a PiInvoice record.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = FactureCallbackPayload.class)))
    @PostMapping("/rapports/factures/reponses")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> receiveInvoice(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        log.debug("CAMT.086 raw payload: {}", payload);
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, null, IsoMessageType.CAMT_086, MessageDirection.INBOUND, payload, 202, null);

        try {
            List<Map<String, Object>> groupes = (List<Map<String, Object>>) payload.get("listeGroupeFacture");
            if (groupes != null) {
                for (Map<String, Object> groupe : groupes) {
                    List<Map<String, Object>> factures = (List<Map<String, Object>>) groupe.get("listeFacture");
                    if (factures != null) {
                        for (Map<String, Object> facture : factures) {
                            PiInvoice invoice = PiInvoice.builder()
                                    .msgId(msgId)
                                    .groupeId((String) groupe.get("groupeId"))
                                    .statementId((String) facture.get("statementId"))
                                    .senderName((String) groupe.get("senderName"))
                                    .senderId((String) groupe.get("senderId"))
                                    .receiverName((String) groupe.get("receiverName"))
                                    .receiverId((String) groupe.get("receiverId"))
                                    .dateDebutFacture(parseDate(facture.get("dateDebutFacture")))
                                    .dateFinFacture(parseDate(facture.get("dateFinFacture")))
                                    .deviseCompte("XOF")
                                    .serviceLines(objectMapper.writeValueAsString(facture.get("donnees")))
                                    .build();
                            invoiceRepository.save(invoice);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist CAMT.086: {}", e.getMessage());
        }

        webhookService.notify(WebhookEventType.INVOICE_RECEIVED, null, msgId, payload);
        return ResponseEntity.accepted().build();
    }
}
