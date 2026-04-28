package ci.sycapay.pispi.service.transfer;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.common.ClientInfo;
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
import ci.sycapay.pispi.service.resolver.ClientSearchResolver;
import ci.sycapay.pispi.service.resolver.ResolvedClient;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityVerificationService {

    private final PiIdentityVerificationRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final ClientSearchResolver clientSearchResolver;
    private final ObjectMapper objectMapper;

    /**
     * Initiate an ACMT.023 identity verification request against another participant.
     * Builds the BCEAO {@code Identite} payload — only msgId, endToEndId,
     * codeMembreParticipant, and one of ibanClient/otherClient.
     */
    @Transactional
    public IdentityVerificationResponse requestVerification(IdentityVerificationRequest request) {
        // Both msgId and endToEndId use the real codeMembre.
        //
        // The BCEAO OpenAPI Identite schema advertises an endToEndId pattern
        // of [BCDF] (excluding EMEs), but the live AIP enforces a different,
        // stronger rule: the endToEndId must start with "E<real-codeMembre>"
        // — a caller-identity cross-check. Empirically the AIP accepts type E
        // at that position (the observed admi.002 reason is "le EndToEndId
        // doit débuter par 'E<codeMembre>'", which means the AIP accepts
        // ECIE002... for an EME caller). A sponsor rewrite would therefore
        // trip the caller-identity check, so we don't split here.
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

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

        log.info("Verification initiated [endToEndId={}, codeMembre={}, ibanClient={}, otherClient={}]",endToEndId, codeMembre, request.getIbanClient(), request.getOtherClient() );
        log.info("ACMT.023 payload: {}", objectMapper.writeValueAsString(acmt023));

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

        // When the caller supplies an endToEndSearch, resolve the client identity
        // from the last inbound RAC_SEARCH and reconstruct the full DTO automatically
        // — same pattern as endToEndIdSearchPayeur in POST /api/v1/transferts.
        if (notBlank(request.getEndToEndSearch())) {
            ResolvedClient resolved = clientSearchResolver.resolve(
                    request.getEndToEndSearch(), "client");
            request = buildFromSearch(resolved, request);
        }

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> acmt024 = new HashMap<>();
        acmt024.put("msgId", msgId);
        acmt024.put("msgIdDemande", v.getMsgId());
        acmt024.put("endToEndId", endToEndId);
        acmt024.put("codeMembreParticipant", codeMembre);
        acmt024.put("resultatVerification", Boolean.toString(Boolean.TRUE.equals(request.getResultatVerification())));

        if (Boolean.TRUE.equals(request.getResultatVerification())) {
            // Account identifier: prefer request value (from endToEndSearch or explicit),
            // fall back to what was stored from the inbound ACMT.023.
            putIfNotBlank(acmt024, "ibanClient",
                    notBlank(request.getIbanClient()) ? request.getIbanClient() : v.getIbanClient());
            putIfNotBlank(acmt024, "otherClient",
                    notBlank(request.getOtherClient()) ? request.getOtherClient() : v.getOtherClient());
            // Rich identity fields — only present when endToEndSearch was used or provided explicitly.
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
            putIfNotBlank(acmt024, "devise", notBlank(request.getDevise()) ? request.getDevise() : "XOF");
        } else {
            putIfNotBlank(acmt024, "codeRaison", request.getCodeRaison());
        }

        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_024,
                MessageDirection.OUTBOUND, acmt024, null, null);
        aipClient.post("/verifications-identites/reponses", acmt024);

        // Persist only the fields that were actually provided — never overwrite
        // existing data (e.g. ibanClient/otherClient from the inbound ACMT.023) with null.
        v.setResultatVerification(request.getResultatVerification());
        v.setCodeRaison(request.getCodeRaison());
        v.setMsgIdReponse(msgId);
        if (Boolean.TRUE.equals(request.getResultatVerification())) {
            if (notBlank(request.getIbanClient()))  v.setIbanClient(request.getIbanClient());
            if (notBlank(request.getOtherClient())) v.setOtherClient(request.getOtherClient());
            if (request.getTypeCompte() != null)    v.setTypeCompte(request.getTypeCompte());
            if (request.getTypeClient() != null)    v.setTypeClient(request.getTypeClient());
            if (notBlank(request.getNomClient()))   v.setNomClient(request.getNomClient());
            if (notBlank(request.getVilleClient())) v.setVilleClient(request.getVilleClient());
            if (notBlank(request.getAdresseComplete()))           v.setAdresseComplete(request.getAdresseComplete());
            if (notBlank(request.getNumeroIdentification()))      v.setNumeroIdentification(request.getNumeroIdentification());
            if (request.getSystemeIdentification() != null)       v.setSystemeIdentification(request.getSystemeIdentification());
            if (notBlank(request.getNumeroRCCMClient()))          v.setNumeroRCCMClient(request.getNumeroRCCMClient());
            if (notBlank(request.getIdentificationFiscaleCommercant())) v.setIdentificationFiscaleCommercant(request.getIdentificationFiscaleCommercant());
            if (notBlank(request.getDateNaissance()))  v.setDateNaissance(LocalDate.parse(request.getDateNaissance()));
            if (notBlank(request.getVilleNaissance())) v.setVilleNaissance(request.getVilleNaissance());
            if (notBlank(request.getPaysNaissance()))  v.setPaysNaissance(request.getPaysNaissance());
            if (notBlank(request.getPaysResidence()))  v.setPaysResidence(request.getPaysResidence());
            if (notBlank(request.getDevise()))         v.setDevise(request.getDevise());
        }
        v.setStatut(Boolean.TRUE.equals(request.getResultatVerification())
                ? VerificationStatus.VERIFIED
                : VerificationStatus.FAILED);
        repository.save(v);

        return toResponse(v);
    }

    private static IdentityVerificationRespondRequest buildFromSearch(
            ResolvedClient c, IdentityVerificationRespondRequest original) {
        ClientInfo info = c.clientInfo();
        return IdentityVerificationRespondRequest.builder()
                .resultatVerification(original.getResultatVerification())
                .codeRaison(original.getCodeRaison())
                .ibanClient(c.iban())
                .otherClient(c.other())
                .typeCompte(c.typeCompte())
                .typeClient(info.getTypeClient())
                .nomClient(info.getNom())
                .villeClient(info.getVille())
                .adresseComplete(info.getAdresse())
                .numeroIdentification(info.getIdentifiant())
                .systemeIdentification(info.getTypeIdentifiant())
                .numeroRCCMClient(c.identificationRccm())
                .identificationFiscaleCommercant(c.identificationFiscaleCommercant())
                .dateNaissance(info.getDateNaissance())
                .villeNaissance(info.getLieuNaissance())
                .paysNaissance(info.getPaysNaissance())
                .paysResidence(info.getPays())
                .devise("XOF")
                .build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (notBlank(value)) map.put(key, value);
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
