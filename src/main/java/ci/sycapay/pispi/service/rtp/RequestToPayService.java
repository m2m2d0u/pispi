package ci.sycapay.pispi.service.rtp;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.rtp.RequestToPayRequest;
import ci.sycapay.pispi.dto.rtp.RequestToPayResponse;
import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.RtpStatus;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RequestToPayService {

    private final PiRequestToPayRepository rtpRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public RequestToPayResponse createRtp(RequestToPayRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> pain013 = new HashMap<>();
        pain013.put("msgId", msgId);
        pain013.put("endToEndId", endToEndId);
        pain013.put("typeTransaction", request.getTypeTransaction().name());
        pain013.put("canalCommunication", request.getCanalCommunication().getCode());
        pain013.put("montant", request.getMontant());
        pain013.put("devise", "XOF");
        pain013.put("codeMembreParticipantPayeur", request.getCodeMembreParticipantPayeur());
        pain013.put("codeMembreParticipantPaye", codeMembre);
        if (request.getDateHeureLimiteAction() != null)
            pain013.put("dateHeureLimiteAction", request.getDateHeureLimiteAction());

        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_013, MessageDirection.OUTBOUND, pain013, null, null);

        PiRequestToPay rtp = PiRequestToPay.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(request.getTypeTransaction())
                .canalCommunication(request.getCanalCommunication())
                .montant(request.getMontant())
                .devise("XOF")
                .codeMembrePayeur(request.getCodeMembreParticipantPayeur())
                .codeMembrePaye(codeMembre)
                .dateHeureLimiteAction(request.getDateHeureLimiteAction())
                .autorisationModificationMontant(request.getAutorisationModificationMontant())
                .statut(RtpStatus.PENDING)
                .build();
        rtpRepository.save(rtp);

        aipClient.post("/api/spi/v{version}/demande-paiement", pain013);

        return toResponse(rtp);
    }

    public RequestToPayResponse getRtp(String endToEndId) {
        PiRequestToPay rtp = rtpRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("RTP", endToEndId));
        return toResponse(rtp);
    }

    public Page<RequestToPayResponse> listRtps(Pageable pageable) {
        return rtpRepository.findByDirection(MessageDirection.OUTBOUND, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public RequestToPayResponse rejectRtp(String endToEndId, String codeRaison) {
        PiRequestToPay rtp = rtpRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("RTP", endToEndId));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> pain014 = new HashMap<>();
        pain014.put("msgId", msgId);
        pain014.put("msgIdDemande", rtp.getMsgId());
        pain014.put("endToEndId", endToEndId);
        pain014.put("statutDemandePaiement", "RJCT");
        pain014.put("codeRaison", codeRaison);

        messageLogService.log(msgId, endToEndId, IsoMessageType.PAIN_014, MessageDirection.OUTBOUND, pain014, null, null);
        aipClient.post("/api/spi/v{version}/demande-paiement/reponse", pain014);

        rtp.setStatut(RtpStatus.RJCT);
        rtp.setCodeRaison(codeRaison);
        rtp.setMsgIdReponse(msgId);
        rtpRepository.save(rtp);

        return toResponse(rtp);
    }

    private RequestToPayResponse toResponse(PiRequestToPay rtp) {
        return RequestToPayResponse.builder()
                .endToEndId(rtp.getEndToEndId())
                .msgId(rtp.getMsgId())
                .identifiantDemandePaiement(rtp.getIdentifiantDemandePaiement())
                .statut(rtp.getStatut())
                .codeRaison(rtp.getCodeRaison())
                .montant(rtp.getMontant())
                .codeMembreParticipantPayeur(rtp.getCodeMembrePayeur())
                .codeMembreParticipantPaye(rtp.getCodeMembrePaye())
                .transferEndToEndId(rtp.getTransferEndToEndId())
                .createdAt(rtp.getCreatedAt() != null ? rtp.getCreatedAt().toString() : null)
                .build();
    }
}
