package ci.sycapay.pispi.controller.callback;

import ci.sycapay.pispi.dto.callback.VerificationCallbackPayload;
import ci.sycapay.pispi.dto.callback.VerificationResultatCallbackPayload;
import ci.sycapay.pispi.entity.PiIdentityVerification;
import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.enums.VerificationStatus;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.repository.PiIdentityVerificationRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Hidden from OpenAPI/Swagger for indirect-participant deployments (EMEs do
 * not initiate or receive direct ACMT.023 traffic — the BCEAO Identite schema
 * restricts initiators to types B/C/D/F). The endpoints remain mounted so a
 * sponsoring direct participant running this PI-SPI can still receive
 * verification callbacks when configured appropriately.
 */
@Hidden
@Tag(name = "Verification Callbacks")
@Slf4j
@RestController
@RequiredArgsConstructor
public class VerificationCallbackController {

    private final MessageLogService messageLogService;
    private final PiIdentityVerificationRepository repository;
    private final WebhookService webhookService;

    @Operation(summary = "Receive inbound verification request (ACMT.023)",
            description = "Called by the AIP when another participant requests identity verification "
                    + "for an account held at this PI. Saves the request locally and fires a "
                    + "VERIFICATION_RECEIVED webhook.")
    @RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = VerificationCallbackPayload.class)))
    @PostMapping("/verifications-identites")
    public ResponseEntity<Void> receiveVerification(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = str(payload, "msgId");
        String endToEndId = str(payload, "endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_023,
                MessageDirection.INBOUND, payload, 202, null);

        PiIdentityVerification verification = PiIdentityVerification.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.INBOUND)
                // The AIP may or may not forward the requester's participant code; keep it optional.
                .codeMembrePayeur(str(payload, "codeMembreParticipantPayeur"))
                .codeMembrePaye(str(payload, "codeMembreParticipant"))
                .codeMembreParticipant(str(payload, "codeMembreParticipant"))
                .ibanClient(str(payload, "ibanClient"))
                .otherClient(str(payload, "otherClient"))
                .statut(VerificationStatus.PENDING)
                .build();
        repository.save(verification);

        webhookService.notify(WebhookEventType.VERIFICATION_RECEIVED, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Receive verification result (ACMT.024)",
            description = "Called by the AIP to deliver the result of a verification request this PI "
                    + "initiated. Updates local verification status and persists the full client "
                    + "identity returned by the payee participant.")
    @RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = VerificationResultatCallbackPayload.class)))
    @PostMapping("/verifications-identites/reponses")
    public ResponseEntity<Void> receiveVerificationResult(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> payload) {
        String msgId = str(payload, "msgId");
        String endToEndId = str(payload, "endToEndId");

        if (messageLogService.isDuplicate(msgId)) return ResponseEntity.accepted().build();
        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_024,
                MessageDirection.INBOUND, payload, 202, null);

        repository.findByEndToEndId(endToEndId).ifPresent(v -> {
            Boolean result = parseBoolean(payload.get("resultatVerification"));
            v.setResultatVerification(result);
            v.setCodeRaison(str(payload, "codeRaison"));
            v.setMsgIdReponse(msgId);

            if (Boolean.TRUE.equals(result)) {
                // Persist the full client identity block from the BCEAO IdentiteReponse.
                String cmp = str(payload, "codeMembreParticipant");
                if (cmp != null) v.setCodeMembreParticipant(cmp);
                String iban = str(payload, "ibanClient");
                if (iban != null) v.setIbanClient(iban);
                String other = str(payload, "otherClient");
                if (other != null) v.setOtherClient(other);

                v.setTypeCompte(parseEnum(str(payload, "typeCompte"), TypeCompte.class));
                v.setTypeClient(parseEnum(str(payload, "typeClient"), TypeClient.class));
                v.setNomClient(str(payload, "nomClient"));
                v.setVilleClient(str(payload, "villeClient"));
                v.setAdresseComplete(str(payload, "adresseComplete"));
                v.setNumeroIdentification(str(payload, "numeroIdentification"));
                v.setSystemeIdentification(parseEnum(str(payload, "systemeIdentification"),
                        CodeSystemeIdentification.class));
                v.setNumeroRCCMClient(str(payload, "numeroRCCMClient"));
                v.setIdentificationFiscaleCommercant(str(payload, "identificationFiscaleCommercant"));
                String dn = str(payload, "dateNaissance");
                if (dn != null) {
                    try {
                        v.setDateNaissance(LocalDate.parse(dn));
                    } catch (Exception e) {
                        log.warn("Ignoring unparseable dateNaissance [{}] in ACMT.024 [endToEndId={}]",
                                dn, endToEndId);
                    }
                }
                v.setVilleNaissance(str(payload, "villeNaissance"));
                v.setPaysNaissance(str(payload, "paysNaissance"));
                v.setPaysResidence(str(payload, "paysResidence"));
                v.setDevise(str(payload, "devise"));
            }

            v.setStatut(Boolean.TRUE.equals(result)
                    ? VerificationStatus.VERIFIED
                    : VerificationStatus.FAILED);
            repository.save(v);
        });

        webhookService.notify(WebhookEventType.VERIFICATION_RESULT, endToEndId, msgId, payload);
        return ResponseEntity.accepted().build();
    }

    // -----------------------------------------------------------------------

    private static String str(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    /** The BCEAO schema serialises resultatVerification as the STRING "true"|"false". */
    private static Boolean parseBoolean(Object raw) {
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
