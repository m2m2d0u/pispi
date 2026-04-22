package ci.sycapay.pispi.service.transfer;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationRespondRequest;
import ci.sycapay.pispi.dto.transfer.IdentityVerificationResponse;
import ci.sycapay.pispi.entity.PiIdentityVerification;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.VerificationStatus;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiIdentityVerificationRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final PiIdentityVerificationRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    /**
     * Initiate an ACMT.023 identity verification request against another participant.
     * Builds the BCEAO {@code Identite} payload — only msgId, endToEndId,
     * codeMembreParticipant, and one of ibanClient/otherClient.
     */
    @Transactional
    public IdentityVerificationResponse requestVerification(IdentityVerificationRequest request) {
        // BCEAO Identite schema (documentation/interface-participant-openapi.json):
        //   msgId      — pattern ^M(country)[BCDEF]\d{3}... → real codeMembre (EMEs allowed)
        //   endToEndId — pattern ^E(country)[BCDF]\d{3}...  → direct-participant only
        // When codeMembre is an EME (type E), the endToEndId must carry a
        // sponsoring direct-participant code configured via
        // sycapay.pi-spi.code-membre-sponsor. Validation checks the resolved
        // sponsor's type (the one that actually lands in endToEndId).
        String codeMembre = properties.getCodeMembre();
        String codeMembreSponsor = properties.resolveCodeMembreSponsor();
        validateInitiatorEligibility(codeMembreSponsor);

        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembreSponsor);

        Map<String, Object> acmt023 = new HashMap<>();
        acmt023.put("msgId", msgId);
        acmt023.put("endToEndId", endToEndId);
        acmt023.put("codeMembreParticipant", request.getCodeMembreParticipant());
        if (request.getIbanClient() != null && !request.getIbanClient().isBlank()) {
            acmt023.put("ibanClient", request.getIbanClient());
        }
        if (request.getOtherClient() != null && !request.getOtherClient().isBlank()) {
            acmt023.put("otherClient", request.getOtherClient());
        }

        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_023,
                MessageDirection.OUTBOUND, acmt023, null, null);

        PiIdentityVerification verification = PiIdentityVerification.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .codeMembrePayeur(codeMembre)
                .codeMembrePaye(request.getCodeMembreParticipant())
                .codeMembreParticipant(request.getCodeMembreParticipant())
                .ibanClient(request.getIbanClient())
                .otherClient(request.getOtherClient())
                .statut(VerificationStatus.PENDING)
                .build();
        repository.save(verification);

        aipClient.post("/verifications-identites", acmt023);

        return toResponse(verification);
    }

    public IdentityVerificationResponse getVerification(String endToEndId) {
        PiIdentityVerification v = repository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification", endToEndId));
        return toResponse(v);
    }

    /**
     * Respond to an inbound ACMT.023 by emitting an ACMT.024 IdentiteReponse.
     * When {@code resultatVerification} is true, populate the full client
     * identity block; when false, include only {@code codeRaison}.
     */
    @Transactional
    public IdentityVerificationResponse respond(String endToEndId,
                                                IdentityVerificationRespondRequest request) {
        PiIdentityVerification v = repository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification", endToEndId));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> acmt024 = new HashMap<>();
        acmt024.put("msgId", msgId);
        acmt024.put("msgIdDemande", v.getMsgId());
        acmt024.put("endToEndId", endToEndId);
        acmt024.put("codeMembreParticipant", codeMembre);
        acmt024.put("resultatVerification", Boolean.toString(Boolean.TRUE.equals(request.getResultatVerification())));

        if (Boolean.TRUE.equals(request.getResultatVerification())) {
            putIfNotBlank(acmt024, "ibanClient", request.getIbanClient());
            putIfNotBlank(acmt024, "otherClient", request.getOtherClient());
            if (request.getTypeCompte() != null) acmt024.put("typeCompte", request.getTypeCompte().name());
            if (request.getTypeClient() != null) acmt024.put("typeClient", request.getTypeClient().name());
            putIfNotBlank(acmt024, "nomClient", request.getNomClient());
            putIfNotBlank(acmt024, "villeClient", request.getVilleClient());
            putIfNotBlank(acmt024, "adresseComplete", request.getAdresseComplete());
            putIfNotBlank(acmt024, "numeroIdentification", request.getNumeroIdentification());
            if (request.getSystemeIdentification() != null) {
                acmt024.put("systemeIdentification", request.getSystemeIdentification().name());
            }
            putIfNotBlank(acmt024, "numeroRCCMClient", request.getNumeroRCCMClient());
            putIfNotBlank(acmt024, "identificationFiscaleCommercant", request.getIdentificationFiscaleCommercant());
            putIfNotBlank(acmt024, "dateNaissance", request.getDateNaissance());
            putIfNotBlank(acmt024, "villeNaissance", request.getVilleNaissance());
            putIfNotBlank(acmt024, "paysNaissance", request.getPaysNaissance());
            putIfNotBlank(acmt024, "paysResidence", request.getPaysResidence());
            putIfNotBlank(acmt024, "devise", request.getDevise() != null ? request.getDevise() : "XOF");
        } else {
            putIfNotBlank(acmt024, "codeRaison", request.getCodeRaison());
        }

        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_024,
                MessageDirection.OUTBOUND, acmt024, null, null);
        aipClient.post("/verifications-identites/reponses", acmt024);

        // Persist result and all rich client info locally
        v.setResultatVerification(request.getResultatVerification());
        v.setCodeRaison(request.getCodeRaison());
        v.setMsgIdReponse(msgId);
        if (Boolean.TRUE.equals(request.getResultatVerification())) {
            if (notBlank(request.getIbanClient())) v.setIbanClient(request.getIbanClient());
            if (notBlank(request.getOtherClient())) v.setOtherClient(request.getOtherClient());
            v.setTypeCompte(request.getTypeCompte());
            v.setTypeClient(request.getTypeClient());
            v.setNomClient(request.getNomClient());
            v.setVilleClient(request.getVilleClient());
            v.setAdresseComplete(request.getAdresseComplete());
            v.setNumeroIdentification(request.getNumeroIdentification());
            v.setSystemeIdentification(request.getSystemeIdentification());
            v.setNumeroRCCMClient(request.getNumeroRCCMClient());
            v.setIdentificationFiscaleCommercant(request.getIdentificationFiscaleCommercant());
            if (notBlank(request.getDateNaissance())) {
                v.setDateNaissance(LocalDate.parse(request.getDateNaissance()));
            }
            v.setVilleNaissance(request.getVilleNaissance());
            v.setPaysNaissance(request.getPaysNaissance());
            v.setPaysResidence(request.getPaysResidence());
            v.setDevise(request.getDevise() != null ? request.getDevise() : "XOF");
        }
        v.setStatut(Boolean.TRUE.equals(request.getResultatVerification())
                ? VerificationStatus.VERIFIED
                : VerificationStatus.FAILED);
        repository.save(v);

        return toResponse(v);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (notBlank(value)) map.put(key, value);
    }

    /**
     * Enforces the BCEAO {@code Identite} endToEndId participant-type
     * constraint ({@code [BCDF]}) against the code that will actually land
     * in the endToEndId — i.e. the resolved sponsor code
     * ({@link PiSpiProperties#resolveCodeMembreSponsor()}).
     *
     * <p>ACMT.023 endToEndId is reserved to direct participants
     * ({@code B|C|D|F}). When {@code codeMembre} is an EME (type {@code E}),
     * a {@code code-membre-sponsor} must be configured; when none is set,
     * the resolver returns {@code codeMembre} itself and this check fails.
     */
    private static void validateInitiatorEligibility(String codeMembreSponsor) {
        if (codeMembreSponsor == null || codeMembreSponsor.length() < 3) {
            throw new IllegalStateException(
                    "sycapay.pi-spi.code-membre is not configured");
        }
        char type = codeMembreSponsor.charAt(2);
        if ("BCDF".indexOf(type) < 0) {
            throw new IllegalArgumentException(
                    "The resolved endToEndId participant code '" + codeMembreSponsor
                            + "' has participant type '" + type + "', which is not allowed by the "
                            + "BCEAO Identite schema (endToEndId pattern [BCDF]). ACMT.023 "
                            + "endToEndId must carry a direct-participant code (B/C/D/F). "
                            + "If sycapay.pi-spi.code-membre is an EME (type E), configure "
                            + "sycapay.pi-spi.code-membre-sponsor with the sponsoring bank's code.");
        }
    }

    private IdentityVerificationResponse toResponse(PiIdentityVerification v) {
        return IdentityVerificationResponse.builder()
                .endToEndId(v.getEndToEndId())
                .msgId(v.getMsgId())
                .statut(v.getStatut())
                .codeMembreParticipant(v.getCodeMembreParticipant())
                .ibanClient(v.getIbanClient())
                .otherClient(v.getOtherClient())
                .resultatVerification(v.getResultatVerification())
                .codeRaison(v.getCodeRaison())
                .typeCompte(v.getTypeCompte())
                .typeClient(v.getTypeClient())
                .nomClient(v.getNomClient())
                .villeClient(v.getVilleClient())
                .adresseComplete(v.getAdresseComplete())
                .numeroIdentification(v.getNumeroIdentification())
                .systemeIdentification(v.getSystemeIdentification())
                .numeroRCCMClient(v.getNumeroRCCMClient())
                .identificationFiscaleCommercant(v.getIdentificationFiscaleCommercant())
                .dateNaissance(v.getDateNaissance() != null ? v.getDateNaissance().toString() : null)
                .villeNaissance(v.getVilleNaissance())
                .paysNaissance(v.getPaysNaissance())
                .paysResidence(v.getPaysResidence())
                .devise(v.getDevise())
                .createdAt(v.getCreatedAt() != null ? v.getCreatedAt().toString() : null)
                .build();
    }
}
