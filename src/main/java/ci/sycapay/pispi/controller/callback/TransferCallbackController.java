package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "Transfer Callbacks")
@Slf4j
@RestController
@RequestMapping("/api/pi/callback")
@RequiredArgsConstructor
public class TransferCallbackController {

    private final MessageLogService messageLogService;
    private final PiTransferRepository transferRepository;
    private final WebhookService webhookService;

    @PostMapping("/virement")
    public ApiResponse<Void> receiveTransfer(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_008, MessageDirection.INBOUND, payload, 200, null);

        PiTransfer transfer = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                .montant(payload.get("montant") != null ? new BigDecimal(String.valueOf(payload.get("montant"))) : null)
                .devise("XOF")
                .codeMembrePayeur((String) payload.get("codeMembreParticipantPayeur"))
                .codeMembrePaye((String) payload.get("codeMembreParticipantPaye"))
                .typeTransaction(TypeTransaction.valueOf((String) payload.get("typeTransaction")))
                .canalCommunication(CanalCommunication.fromCode((String) payload.get("canalCommunication")))
                .nomClientPayeur((String) payload.get("nomClientPayeur"))
                .nomClientPaye((String) payload.get("nomClientPaye"))
                .statut(TransferStatus.PEND)
                .build();
        transferRepository.save(transfer);

        webhookService.notify(WebhookEventType.TRANSFER_RECEIVED, endToEndId, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/virement/resultat")
    public ApiResponse<Void> receiveTransferResult(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");
        String endToEndId = (String) payload.get("endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_002, MessageDirection.INBOUND, payload, 200, null);

        transferRepository.findByEndToEndId(endToEndId).ifPresent(transfer -> {
            String statut = (String) payload.get("statutTransaction");
            transfer.setStatut(TransferStatus.valueOf(statut));
            transfer.setCodeRaison((String) payload.get("codeRaison"));
            transfer.setMsgIdReponse(msgId);
            transfer.setDateHeureIrrevocabilite(parseDateTime(payload.get("dateHeureIrrevocabilite")));
            transferRepository.save(transfer);
        });

        webhookService.notify(WebhookEventType.TRANSFER_RESULT, endToEndId, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }

    @PostMapping("/rejet")
    public ApiResponse<Void> receiveRejection(@RequestBody Map<String, Object> payload) {
        String msgId = (String) payload.get("msgId");

        if (messageLogService.isDuplicate(msgId)) return ApiResponse.ok(null);
        messageLogService.log(msgId, null, IsoMessageType.ADMI_002, MessageDirection.INBOUND, payload, 200, null);

        webhookService.notify(WebhookEventType.MESSAGE_REJECTED, null, msgId, payload);
        return ApiResponse.ok("Callback received", null);
    }
}
