package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDate;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "Report Callbacks")
@Slf4j
@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class ReportCallbackController {

    private final MessageLogService messageLogService;
    private final PiTransactionReportRepository transactionReportRepository;
    private final PiCompensationRepository compensationRepository;
    private final PiGuaranteeRepository guaranteeRepository;
    private final PiInvoiceRepository invoiceRepository;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/releve")
    public ApiResponse<Void> receiveTransactionReport(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.CAMT_052, MessageDirection.INBOUND, payload, 200, null);

        try {
            PiTransactionReport report = PiTransactionReport.builder()
                    .msgId(msgId)
                    .identifiantReleve((String) payload.get("identifiantReleve"))
                    .pageCourante(payload.get("pageCourante") != null ? (Integer) payload.get("pageCourante") : null)
                    .dernierePage(payload.get("dernierePage") != null ? (Boolean) payload.get("dernierePage") : null)
                    .nbreTotalTransaction(payload.get("nbreTotalTransaction") != null ? (Integer) payload.get("nbreTotalTransaction") : null)
                    .indicateurSolde(payload.get("indicateurSolde") != null ? IndicateurSolde.valueOf((String) payload.get("indicateurSolde")) : null)
                    .transactions(objectMapper.writeValueAsString(payload.get("transactions")))
                    .build();
            transactionReportRepository.save(report);
        } catch (Exception e) {
            log.error("Failed to persist CAMT.052: {}", e.getMessage());
        }

        webhookService.notify(WebhookEventType.TRANSACTION_REPORT, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/compensation")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> receiveCompensation(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.CAMT_053, MessageDirection.INBOUND, payload, 200, null);

        List<Map<String, Object>> soldes = (List<Map<String, Object>>) payload.get("soldes");
        if (soldes != null) {
            for (Map<String, Object> solde : soldes) {
                PiCompensation compensation = PiCompensation.builder()
                        .msgId(msgId)
                        .soldeId((String) solde.get("id"))
                        .participant((String) solde.get("participant"))
                        .participantSponsor((String) solde.get("participantSponsor"))
                        .balanceType(solde.get("balanceType") != null ? TypeBalanceCompense.valueOf((String) solde.get("balanceType")) : null)
                        .montant(solde.get("montant") != null ? new BigDecimal(String.valueOf(solde.get("montant"))) : null)
                        .operationType(solde.get("operationType") != null ? IndicateurSolde.valueOf((String) solde.get("operationType")) : null)
                        .dateBalance(parseDateTime(solde.get("dateBalance")))
                        .build();
                compensationRepository.save(compensation);
            }
        }

        webhookService.notify(WebhookEventType.COMPENSATION_REPORT, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/garantie")
    public ApiResponse<Void> receiveGuarantee(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.CAMT_010, MessageDirection.INBOUND, payload, 200, null);

        try {
            PiGuarantee guarantee = PiGuarantee.builder()
                    .msgId(msgId)
                    .sourceMessageType(IsoMessageType.CAMT_010)
                    .montantGarantie(payload.get("montantGarantie") != null ?
                            new BigDecimal(String.valueOf(payload.get("montantGarantie"))) : null)
                    .montantRestantGarantie(payload.get("montantRestantGarantie") != null ?
                            new BigDecimal(String.valueOf(payload.get("montantRestantGarantie"))) : null)
                    .typeOperationGarantie(payload.get("typeOperationGarantie") != null ? TypeOperationGarantie.valueOf((String) payload.get("typeOperationGarantie")) : null)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            guaranteeRepository.save(guarantee);
        } catch (Exception e) {
            log.error("Failed to persist CAMT.010: {}", e.getMessage());
        }

        webhookService.notify(WebhookEventType.GUARANTEE_UPDATED, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/facture")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> receiveInvoice(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.CAMT_086, MessageDirection.INBOUND, payload, 200, null);

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
        return ApiResponse.ok("Callback received", null);
    }
}
