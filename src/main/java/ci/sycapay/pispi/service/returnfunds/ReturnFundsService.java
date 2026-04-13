package ci.sycapay.pispi.service.returnfunds;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.returnfunds.ReturnAcceptRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.entity.PiReturnExecution;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.ReturnRequestStatus;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiReturnExecutionRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReturnFundsService {

    private final PiReturnRequestRepository returnRequestRepository;
    private final PiReturnExecutionRepository returnExecutionRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public ReturnFundsResponse requestReturn(ReturnFundsRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String identifiantDemande = IdGenerator.generateReturnRequestId(codeMembre);

        Map<String, Object> camt056 = new HashMap<>();
        camt056.put("msgId", msgId);
        camt056.put("identifiantDemandeRetourFonds", identifiantDemande);
        camt056.put("endToEndId", request.getEndToEndId());
        camt056.put("raison", request.getRaison().name());

        messageLogService.log(msgId, request.getEndToEndId(), IsoMessageType.CAMT_056, MessageDirection.OUTBOUND, camt056, null, null);

        PiReturnRequest returnReq = PiReturnRequest.builder()
                .msgId(msgId)
                .identifiantDemande(identifiantDemande)
                .endToEndId(request.getEndToEndId())
                .direction(MessageDirection.OUTBOUND)
                .codeMembrePayeur(codeMembre)
                .raison(request.getRaison())
                .statut(ReturnRequestStatus.PENDING)
                .build();
        returnRequestRepository.save(returnReq);

        aipClient.post("/retour-fonds/demande", camt056);

        return toResponse(returnReq);
    }

    public ReturnFundsResponse getReturnRequest(String identifiantDemande) {
        PiReturnRequest req = returnRequestRepository.findByIdentifiantDemande(identifiantDemande)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", identifiantDemande));
        return toResponse(req);
    }

    @Transactional
    public ReturnFundsResponse rejectReturn(String identifiantDemande, ReturnRejectRequest request) {
        PiReturnRequest returnReq = returnRequestRepository.findByIdentifiantDemande(identifiantDemande)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", identifiantDemande));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> camt029 = new HashMap<>();
        camt029.put("msgId", msgId);
        camt029.put("msgIdDemande", returnReq.getMsgId());
        camt029.put("identifiantDemandeRetourFonds", identifiantDemande);
        camt029.put("statut", "RJCR");
        camt029.put("raison", request.getRaison().name());

        messageLogService.log(msgId, returnReq.getEndToEndId(), IsoMessageType.CAMT_029, MessageDirection.OUTBOUND, camt029, null, null);
        aipClient.post("/retour-fonds/reponses", camt029);

        returnReq.setStatut(ReturnRequestStatus.RJCR);
        returnReq.setRaisonRejet(request.getRaison());
        returnReq.setMsgIdRejet(msgId);
        returnRequestRepository.save(returnReq);

        return toResponse(returnReq);
    }

    @Transactional
    public ReturnFundsResponse acceptReturn(String identifiantDemande, ReturnAcceptRequest request) {
        PiReturnRequest returnReq = returnRequestRepository.findByIdentifiantDemande(identifiantDemande)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnRequest", identifiantDemande));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> pacs004 = new HashMap<>();
        pacs004.put("msgId", msgId);
        pacs004.put("endToEndId", endToEndId);
        pacs004.put("montantRetourne", request.getMontantRetourne());
        pacs004.put("raisonRetour", request.getRaisonRetour().name());

        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_004, MessageDirection.OUTBOUND, pacs004, null, null);
        aipClient.post("/retour-fonds", pacs004);

        PiReturnExecution execution = PiReturnExecution.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .montantRetourne(request.getMontantRetourne())
                .raisonRetour(request.getRaisonRetour())
                .returnRequestId(returnReq.getId())
                .build();
        returnExecutionRepository.save(execution);

        returnReq.setStatut(ReturnRequestStatus.ACCEPTED);
        returnRequestRepository.save(returnReq);

        ReturnFundsResponse resp = toResponse(returnReq);
        resp.setMontantRetourne(request.getMontantRetourne());
        return resp;
    }

    private ReturnFundsResponse toResponse(PiReturnRequest r) {
        return ReturnFundsResponse.builder()
                .identifiantDemande(r.getIdentifiantDemande())
                .endToEndId(r.getEndToEndId())
                .statut(r.getStatut())
                .raison(r.getRaison())
                .raisonRejet(r.getRaisonRejet())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
                .build();
    }
}
