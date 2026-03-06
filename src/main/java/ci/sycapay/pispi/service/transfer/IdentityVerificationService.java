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

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final PiIdentityVerificationRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public IdentityVerificationResponse requestVerification(IdentityVerificationRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> acmt023 = new HashMap<>();
        acmt023.put("msgId", msgId);
        acmt023.put("endToEndId", endToEndId);
        acmt023.put("codeMembreParticipantPayeur", codeMembre);
        acmt023.put("codeMembreParticipantPaye", request.getCodeMembreParticipantPaye());
        acmt023.put("numeroCompteClientPaye", request.getNumeroCompteClientPaye());
        acmt023.put("typeCompteClientPaye", request.getTypeCompteClientPaye().name());
        if (request.getNomClientPaye() != null) acmt023.put("nomClientPaye", request.getNomClientPaye());
        if (request.getPrenomClientPaye() != null) acmt023.put("prenomClientPaye", request.getPrenomClientPaye());

        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_023, MessageDirection.OUTBOUND, acmt023, null, null);

        PiIdentityVerification verification = PiIdentityVerification.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .codeMembrePayeur(codeMembre)
                .codeMembrePaye(request.getCodeMembreParticipantPaye())
                .numeroComptePaye(request.getNumeroCompteClientPaye())
                .typeComptePaye(request.getTypeCompteClientPaye().name())
                .nomClientPaye(request.getNomClientPaye())
                .prenomClientPaye(request.getPrenomClientPaye())
                .statut(VerificationStatus.PENDING)
                .build();
        repository.save(verification);

        aipClient.post("/api/spi/v{version}/verification", acmt023);

        return toResponse(verification);
    }

    public IdentityVerificationResponse getVerification(String endToEndId) {
        PiIdentityVerification v = repository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification", endToEndId));
        return toResponse(v);
    }

    @Transactional
    public IdentityVerificationResponse respond(String endToEndId, IdentityVerificationRespondRequest request) {
        PiIdentityVerification v = repository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification", endToEndId));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> acmt024 = new HashMap<>();
        acmt024.put("msgId", msgId);
        acmt024.put("msgIdDemande", v.getMsgId());
        acmt024.put("endToEndId", endToEndId);
        acmt024.put("resultatVerification", request.getResultatVerification());
        if (request.getCodeRaison() != null) acmt024.put("codeRaison", request.getCodeRaison());

        messageLogService.log(msgId, endToEndId, IsoMessageType.ACMT_024, MessageDirection.OUTBOUND, acmt024, null, null);
        aipClient.post("/api/spi/v{version}/verification/reponse", acmt024);

        v.setResultatVerification(request.getResultatVerification());
        v.setCodeRaison(request.getCodeRaison());
        v.setMsgIdReponse(msgId);
        v.setStatut(request.getResultatVerification() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
        repository.save(v);

        return toResponse(v);
    }

    private IdentityVerificationResponse toResponse(PiIdentityVerification v) {
        return IdentityVerificationResponse.builder()
                .endToEndId(v.getEndToEndId())
                .msgId(v.getMsgId())
                .statut(v.getStatut())
                .resultatVerification(v.getResultatVerification())
                .codeRaison(v.getCodeRaison())
                .createdAt(v.getCreatedAt() != null ? v.getCreatedAt().toString() : null)
                .build();
    }
}
