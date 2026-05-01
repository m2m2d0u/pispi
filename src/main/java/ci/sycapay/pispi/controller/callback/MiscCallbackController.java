package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.common.ApiResponse;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.alias.AliasCallbackService;
import ci.sycapay.pispi.service.alias.RevendicationService;
import ci.sycapay.pispi.service.callback.Admi002CallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

@Tag(name = "Misc Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class MiscCallbackController {

    private final MessageLogService messageLogService;
    private final AliasCallbackService aliasCallbackService;
    private final RevendicationService revendicationService;
    private final Admi002CallbackService admi002CallbackService;

    // ------------------------------------------------------------------
    // ADMI.002 rejection callbacks
    //
    // BCEAO routes structural rejections to several /echecs endpoints
    // depending on the message family. Each endpoint knows what type of
    // message was rejected and passes that hint to Admi002CallbackService,
    // which applies the right domain-side update and fires a
    // MESSAGE_REJECTED webhook.
    // ------------------------------------------------------------------

    @Operation(summary = "Receive notification/report-request failure (ADMI.002)",
            description = "Endpoint AIP générique pour les rejets touchant des messages "
                    + "envoyés sur le domaine notifications/rapports : CAMT.060 (demande "
                    + "de rapport), ADMI.011 (accusé), etc. Le type réel du message rejeté "
                    + "est résolu via lookup msgId dans pi_message_log — on passe donc "
                    + "{@code hint=null} pour laisser Admi002CallbackService faire son "
                    + "travail. Hardcoder ADMI_004 ici masquait la vraie nature du message "
                    + "rejeté (cf. CAMT.060 TransactionNotFound vu en prod).")
    @PostMapping("/notifications/echecs")
    public ResponseEntity<ApiResponse<Void>> receiveNotificationFailure(
            @RequestBody Map<String, Object> payload) {
        log.debug("Notification/report ADMI.002 raw payload: {}", payload);
        admi002CallbackService.handleRejection(payload, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive acmt.023 verification failure (ADMI.002)")
    @PostMapping("/verifications-identites/echecs")
    public ResponseEntity<ApiResponse<Void>> receiveVerificationFailure(
            @RequestBody Map<String, Object> payload) {
        log.debug("ACMT.023 verification failure raw payload: {}", payload);
        admi002CallbackService.handleRejection(payload, IsoMessageType.ACMT_023);
        return ResponseEntity.accepted().build();
    }

    // ---- Participant list response ----

    @Operation(summary = "Receive participant list response")
    @PostMapping("/participants/liste/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveParticipantList(@RequestBody Map<String, Object> payload) {
        log.debug("Participant list raw payload: {}", payload);
        messageLogService.log(null, null, IsoMessageType.REDA_014, MessageDirection.INBOUND, payload, 202, null);
        return ResponseEntity.accepted().build();
    }

    // ---- Alias responses ----

    @Operation(summary = "Receive alias search response")
    @PostMapping("/alias/recherche/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasSearchResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Alias search response raw payload: {}", payload);
        String endToEndId = (String) payload.get("endToEndId");
        messageLogService.log(null, endToEndId, IsoMessageType.RAC_SEARCH, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update or create alias with data from PI-RAC
//        aliasCallbackService.processSearchResponse(payload);

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive alias creation response")
    @PostMapping("/alias/creation/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasCreationResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Alias creation response raw payload: {}", payload);
        String idCreationAlias = (String) payload.get("idCreationAlias");
        if (idCreationAlias != null && messageLogService.isDuplicate(idCreationAlias)) {
            return ResponseEntity.accepted().build();
        }
        messageLogService.log(null, idCreationAlias, IsoMessageType.RAC_CREATE, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update alias with actual values from PI-RAC
        aliasCallbackService.processCreationResponse(payload);

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive alias modification response")
    @PostMapping("/alias/modification/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasModificationResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Alias modification response raw payload: {}", payload);
        String alias = (String) payload.get("alias");
        if (alias != null && messageLogService.isDuplicate("MOD_" + alias)) {
            return ResponseEntity.accepted().build();
        }
        messageLogService.log(null, "MOD_" + alias, IsoMessageType.RAC_MODIFY, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update alias with dateModification from PI-RAC
        aliasCallbackService.processModificationResponse(payload);

        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive alias deletion response")
    @PostMapping("/alias/suppression/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveAliasDeletionResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Alias deletion response raw payload: {}", payload);
        String alias = (String) payload.get("alias");
        if (alias != null && messageLogService.isDuplicate("DEL_" + alias)) {
            return ResponseEntity.accepted().build();
        }
        messageLogService.log(null, "DEL_" + alias, IsoMessageType.RAC_DELETE, MessageDirection.INBOUND, payload, 202, null);

        // Process callback: update alias status to DELETED and set dateSuppressionRac
        aliasCallbackService.processDeletionResponse(payload);

        return ResponseEntity.accepted().build();
    }

    // ---- Revendication responses ----

    @Operation(summary = "Receive claim response")
    @PostMapping("/revendications/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Claim response raw payload: {}", payload);
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        messageLogService.log(null, identifiantRevendication, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        revendicationService.processClaimResponse(payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive claim recovery response")
    @PostMapping("/revendications/recuperation/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimRecoveryResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Claim recovery response raw payload: {}", payload);
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        messageLogService.log(null, identifiantRevendication, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        revendicationService.processClaimRecuperationResponse(payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive claim acceptance response")
    @PostMapping("/revendications/acceptation/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimAcceptanceResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Claim acceptance response raw payload: {}", payload);
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        messageLogService.log(null, identifiantRevendication, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        revendicationService.processClaimAcceptationResponse(payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive claim rejection response")
    @PostMapping("/revendications/rejet/reponses")
    public ResponseEntity<ApiResponse<Void>> receiveClaimRejectionResponse(@RequestBody Map<String, Object> payload) {
        log.debug("Claim rejection response raw payload: {}", payload);
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        messageLogService.log(null, identifiantRevendication, IsoMessageType.RAC_REVENDICATION, MessageDirection.INBOUND, payload, 202, null);
        revendicationService.processClaimRejetResponse(payload);
        return ResponseEntity.accepted().build();
    }

    // ------------------------------------------------------------------
    // Generic ISO / HTTP failure endpoints — the AIP doesn't tell us the
    // rejected message's type here, so we let Admi002CallbackService resolve
    // it via pi_message_log lookup on the msgIdDemande reference.
    // ------------------------------------------------------------------

    @Operation(summary = "Receive HTTP send failure notification (ADMI.002)")
    @PostMapping("/message-envoi/echec-http")
    public ResponseEntity<ApiResponse<Void>> receiveHttpSendFailure(
            @RequestBody Map<String, Object> payload) {
        log.debug("HTTP message send failure raw payload: {}", payload);
        admi002CallbackService.handleRejection(payload, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive message processing failure (ADMI.002)")
    @PostMapping("/message-traitement/echec")
    public ResponseEntity<ApiResponse<Void>> receiveProcessingFailure(
            @RequestBody Map<String, Object> payload) {
        log.debug("Message processing failure raw payload: {}", payload);
        admi002CallbackService.handleRejection(payload, null);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive ISO message send failure (ADMI.002)",
            description = "Generic structural/delivery rejection. The service resolves the "
                    + "rejected message type via pi_message_log, flips the matching transfer / "
                    + "RTP / verification / return / alias row to its FAILED state and carries "
                    + "detailEchec + codeRaisonRejet onto the entity; a MESSAGE_REJECTED webhook "
                    + "is fired so the back office can react.")
    @PostMapping("/messages-iso/echec-envoi")
    public ResponseEntity<ApiResponse<Void>> receiveIsoSendFailure(
            @RequestBody Map<String, Object> payload) {
        log.debug("ISO message send failure raw payload: {}", payload);
        admi002CallbackService.handleRejection(payload, null);
        return ResponseEntity.accepted().build();
    }

}
