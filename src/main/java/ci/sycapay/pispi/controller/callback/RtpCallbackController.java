package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.callback.DemandePaiementCallbackPayload;
import ci.sycapay.pispi.dto.callback.DemandePaiementResultatCallbackPayload;
import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.CanalCommunicationRtp;
import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.CodeTypeDocument;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.RtpStatus;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Tag(name = "RTP Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class RtpCallbackController {

    private final MessageLogService messageLogService;
    private final PiRequestToPayRepository rtpRepository;
    private final WebhookService webhookService;

    @Operation(summary = "Receive inbound Request-to-Pay (PAIN.013)",
            description = "Called by the AIP when another participant sends a Request-to-Pay to this "
                    + "PI. Parses the flat BCEAO DemandePaiement schema, saves the RTP locally and "
                    + "fires an RTP_RECEIVED webhook so the backend can prompt the payer.")
    @RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = DemandePaiementCallbackPayload.class)))
    @PostMapping("/demandes-paiements")
    public ResponseEntity<Void> receiveRtp(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = str(payload, "msgId");
        String endToEndId = str(payload, "endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_013,
                MessageDirection.INBOUND, payload, 202, null);

        PiRequestToPay rtp = PiRequestToPay.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .identifiantDemandePaiement(str(payload, "identifiantDemandePaiement"))
                .referenceBulk(str(payload, "referenceBulk"))
                .direction(MessageDirection.INBOUND)
                .montant(parseBigDecimal(payload.get("montant")))
                .devise("XOF")
                .canalCommunication(parseCanal(str(payload, "canalCommunication")))
                .dateHeureLimiteAction(parseDateTime(payload.get("dateHeureLimiteAction")))
                .autorisationModificationMontant(parseBooleanLoose(payload.get("autorisationModificationMontant")))
                // Payeur
                .codeMembrePayeur(str(payload, "codeMembreParticipantPayeur"))
                .aliasClientPayeur(str(payload, "aliasClientPayeur"))
                .ibanClientPayeur(str(payload, "ibanClientPayeur"))
                .otherClientPayeur(str(payload, "otherClientPayeur"))
                .typeComptePayeur(parseEnum(str(payload, "typeCompteClientPayeur"), TypeCompte.class))
                .nomClientPayeur(str(payload, "nomClientPayeur"))
                .typeClientPayeur(parseEnum(str(payload, "typeClientPayeur"), TypeClient.class))
                .villeClientPayeur(str(payload, "villeClientPayeur"))
                .paysClientPayeur(str(payload, "paysClientPayeur"))
                .numeroIdentificationPayeur(str(payload, "numeroIdentificationClientPayeur"))
                .systemeIdentificationPayeur(parseEnum(str(payload, "systemeIdentificationClientPayeur"),
                        CodeSystemeIdentification.class))
                .numeroRCCMPayeur(str(payload, "numeroRCCMClientPayeur"))
                .dateNaissancePayeur(str(payload, "dateNaissanceClientPayeur"))
                .villeNaissancePayeur(str(payload, "villeNaissanceClientPayeur"))
                .paysNaissancePayeur(str(payload, "paysNaissanceClientPayeur"))
                .identificationFiscalePayeur(str(payload, "identificationFiscaleCommercantPayeur"))
                // Payé (this PI)
                .codeMembrePaye(str(payload, "codeMembreParticipantPaye"))
                .aliasClientPaye(str(payload, "aliasClientPaye"))
                .ibanClientPaye(str(payload, "ibanClientPaye"))
                .otherClientPaye(str(payload, "otherClientPaye"))
                .typeComptePaye(parseEnum(str(payload, "typeCompteClientPaye"), TypeCompte.class))
                .nomClientPaye(str(payload, "nomClientPaye"))
                .typeClientPaye(parseEnum(str(payload, "typeClientPaye"), TypeClient.class))
                .villeClientPaye(str(payload, "villeClientPaye"))
                .paysClientPaye(str(payload, "paysClientPaye"))
                .numeroIdentificationPaye(str(payload, "numeroIdentificationClientPaye"))
                .systemeIdentificationPaye(parseEnum(str(payload, "systemeIdentificationClientPaye"),
                        CodeSystemeIdentification.class))
                .numeroRCCMPaye(str(payload, "numeroRCCMClientPaye"))
                .dateNaissancePaye(str(payload, "dateNaissanceClientPaye"))
                .villeNaissancePaye(str(payload, "villeNaissanceClientPaye"))
                .paysNaissancePaye(str(payload, "paysNaissanceClientPaye"))
                .identificationFiscalePaye(str(payload, "identificationFiscaleCommercantPaye"))
                .latitudeClientPaye(str(payload, "latitudeClientPaye"))
                .longitudeClientPaye(str(payload, "longitudeClientPaye"))
                // Transaction details
                .motif(str(payload, "motif"))
                .typeDocumentReference(parseEnum(str(payload, "typeDocumentReference"),
                        CodeTypeDocument.class))
                .numeroDocumentReference(str(payload, "numeroDocumentReference"))
                .statut(RtpStatus.PENDING)
                .build();
        rtpRepository.save(rtp);

        webhookService.notify(WebhookEventType.RTP_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive RTP result (PAIN.014)",
            description = "Called by the AIP to deliver the payer's reject decision on a Request-to-Pay "
                    + "this PI initiated. Uses the BCEAO 'statut' field (not 'statutDemandePaiement'). "
                    + "Updates local RTP status and fires an RTP_RESULT webhook.")
    @RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = DemandePaiementResultatCallbackPayload.class)))
    @PostMapping("/demandes-paiements/reponses")
    public ResponseEntity<Void> receiveRtpResult(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = str(payload, "msgId");
        String endToEndId = str(payload, "endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_014,
                MessageDirection.INBOUND, payload, 202, null);

        // BCEAO ReponseDemandePaiement uses "statut" — accept both names
        // defensively in case of legacy AIPs sending "statutDemandePaiement".
        String statut = str(payload, "statut");
        if (statut == null) statut = str(payload, "statutDemandePaiement");
        final String finalStatut = statut;

        rtpRepository.findByEndToEndId(endToEndId).ifPresent(rtp -> {
            if ("RJCT".equalsIgnoreCase(finalStatut)) {
                rtp.setStatut(RtpStatus.RJCT);
            }
            rtp.setCodeRaison(str(payload, "codeRaison"));
            rtp.setMsgIdReponse(msgId);
            rtpRepository.save(rtp);
        });

        webhookService.notify(WebhookEventType.RTP_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    // =======================================================================

    private static String str(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    private static BigDecimal parseBigDecimal(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** BCEAO RTP canal codes come through as strings like "500" / "631". */
    private static CanalCommunicationRtp parseCanal(String code) {
        if (code == null) return null;
        try {
            return CanalCommunicationRtp.fromCode(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** BCEAO serialises booleans as the strings "true"|"false"; still accept native booleans. */
    private static Boolean parseBooleanLoose(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        String s = raw.toString().trim();
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> cls) {
        if (value == null) return null;
        try {
            return Enum.valueOf(cls, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
