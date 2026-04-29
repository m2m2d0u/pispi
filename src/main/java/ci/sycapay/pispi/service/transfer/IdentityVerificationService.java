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
        log.debug("ACMT.023 payload: {}", objectMapper.writeValueAsString(acmt023));

        aipClient.post("/verifications-identites", acmt023);

        return toResponse(verification);
    }

    public IdentityVerificationResponse getVerification(String endToEndId) {
        // Direction-agnostic public lookup. Composite unique on
        // (end_to_end_id, direction) (V42) means both legs may exist locally —
        // return the most recent.
        PiIdentityVerification v = repository.findFirstByEndToEndIdOrderByIdDesc(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification", endToEndId));
        return toResponse(v);
    }

    /**
     * Respond to an inbound ACMT.023 by emitting an ACMT.024 IdentiteReponse.
     * When {@code resultatVerification} is true, populate the full client
     * identity block; when false, include only {@code codeRaison}.
     */
    /**
     * Réponse à une demande de vérification d'identité entrante (ACMT.023).
     *
     * <p>Modèle simplifié : le caller fournit uniquement {@code resultatVerification}
     * (et {@code codeRaison} si rejet). Quand {@code resultatVerification = true},
     * il fournit AUSSI {@code endToEndSearch} — le service y résout TOUS les
     * champs d'identité (compte, type, nom, adresse, identification, naissance,
     * pays) depuis la RAC_SEARCH déjà journalisée. Pas de saisie redondante.
     */
    @Transactional
    public IdentityVerificationResponse respond(String endToEndId,
                                                IdentityVerificationRespondRequest request) {
        // Responding to an inbound ACMT.023 — the local row is INBOUND.
        PiIdentityVerification v = repository
                .findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("Verification", endToEndId));

        boolean accept = Boolean.TRUE.equals(request.getResultatVerification());

        // Résolution du client UNIQUEMENT en cas d'acceptation. Le DTO impose
        // {@code endToEndSearch} non-null sur le chemin succès via @AssertTrue.
        ResolvedClient resolved = accept
                ? clientSearchResolver.resolve(request.getEndToEndSearch(), "client")
                : null;

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> acmt024 = new HashMap<>();
        acmt024.put("msgId", msgId);
        acmt024.put("msgIdDemande", v.getMsgId());
        acmt024.put("endToEndId", endToEndId);
        // codeMembreParticipant in ACMT.024 maps to Assgne (the original requester),
        // while the AIP derives Assgnr from our own credentials. Sending our own code
        // here makes Assgnr == Assgne and triggers "Assgnr doit être différent de Assgne".
        acmt024.put("codeMembreParticipant", v.getCodeMembreParticipant());
        acmt024.put("resultatVerification", Boolean.toString(accept));

        if (accept) {
            ClientInfo info = resolved.clientInfo();
            // Compte : un seul de ibanClient / otherClient (le resolver garantit
            // mutuelle exclusivité par construction).
            putIfNotBlank(acmt024, "ibanClient", resolved.iban());
            putIfNotBlank(acmt024, "otherClient", resolved.other());
            if (resolved.typeCompte() != null) acmt024.put("typeCompte", resolved.typeCompte().name());
            if (info.getTypeClient() != null) acmt024.put("typeClient", info.getTypeClient().name());
            putIfNotBlank(acmt024, "nomClient", info.getNom());
            putIfNotBlank(acmt024, "villeClient", info.getVille());
            putIfNotBlank(acmt024, "adresseComplete", info.getAdresse());
            putIfNotBlank(acmt024, "numeroIdentification", info.getIdentifiant());
            if (info.getTypeIdentifiant() != null) {
                acmt024.put("systemeIdentification", info.getTypeIdentifiant().name());
            }
            putIfNotBlank(acmt024, "numeroRCCMClient", resolved.identificationRccm());
            putIfNotBlank(acmt024, "identificationFiscaleCommercant", resolved.identificationFiscaleCommercant());
            putIfNotBlank(acmt024, "dateNaissance", info.getDateNaissance());
            putIfNotBlank(acmt024, "villeNaissance", info.getLieuNaissance());
            putIfNotBlank(acmt024, "paysNaissance", info.getPaysNaissance());
            putIfNotBlank(acmt024, "paysResidence", info.getPays());
            acmt024.put("devise", "XOF");
        } else {
            // BCEAO XSD fixes Cd to 'AC01' — always send it, defaulting when absent.
            acmt024.put("codeRaison", notBlank(request.getCodeRaison()) ? request.getCodeRaison() : "AC01");
        }

        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_024,
                MessageDirection.OUTBOUND, acmt024, null, null);
        aipClient.post("/verifications-identites/reponses", acmt024);

        // Persistance : on ne snapshot l'identité résolue que sur succès.
        v.setResultatVerification(accept);
        v.setCodeRaison(request.getCodeRaison());
        v.setMsgIdReponse(msgId);
        if (accept) {
            ClientInfo info = resolved.clientInfo();
            if (notBlank(resolved.iban()))  v.setIbanClient(resolved.iban());
            if (notBlank(resolved.other())) v.setOtherClient(resolved.other());
            if (resolved.typeCompte() != null) v.setTypeCompte(resolved.typeCompte());
            if (info.getTypeClient() != null)  v.setTypeClient(info.getTypeClient());
            if (notBlank(info.getNom()))       v.setNomClient(info.getNom());
            if (notBlank(info.getVille()))     v.setVilleClient(info.getVille());
            if (notBlank(info.getAdresse()))   v.setAdresseComplete(info.getAdresse());
            if (notBlank(info.getIdentifiant())) v.setNumeroIdentification(info.getIdentifiant());
            if (info.getTypeIdentifiant() != null) v.setSystemeIdentification(info.getTypeIdentifiant());
            if (notBlank(resolved.identificationRccm())) v.setNumeroRCCMClient(resolved.identificationRccm());
            if (notBlank(resolved.identificationFiscaleCommercant()))
                v.setIdentificationFiscaleCommercant(resolved.identificationFiscaleCommercant());
            if (notBlank(info.getDateNaissance()))  v.setDateNaissance(LocalDate.parse(info.getDateNaissance()));
            if (notBlank(info.getLieuNaissance()))  v.setVilleNaissance(info.getLieuNaissance());
            if (notBlank(info.getPaysNaissance()))  v.setPaysNaissance(info.getPaysNaissance());
            if (notBlank(info.getPays()))           v.setPaysResidence(info.getPays());
            v.setDevise("XOF");
        }
        v.setStatut(accept ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
        repository.save(v);

        return toResponse(v);
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
