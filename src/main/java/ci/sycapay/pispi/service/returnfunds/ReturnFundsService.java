package ci.sycapay.pispi.service.returnfunds;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.returnfunds.ReturnAcceptRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsRequest;
import ci.sycapay.pispi.dto.returnfunds.ReturnFundsResponse;
import ci.sycapay.pispi.dto.returnfunds.ReturnRejectRequest;
import ci.sycapay.pispi.entity.PiReturnExecution;
import ci.sycapay.pispi.entity.PiReturnRequest;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.ReturnRequestStatus;
import ci.sycapay.pispi.enums.TransferStatus;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiReturnExecutionRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnFundsService {

    private final PiReturnRequestRepository returnRequestRepository;
    private final PiReturnExecutionRepository returnExecutionRepository;
    private final PiTransferRepository transferRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public ReturnFundsResponse requestReturn(ReturnFundsRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        // Spec §4.8.1 CAMT.056: msgId, codeMembreParticipantPaye, endToEndId, raison
        Map<String, Object> camt056 = new HashMap<>();
        camt056.put("msgId", msgId);
        camt056.put("codeMembreParticipantPaye", request.getCodeMembreParticipantPaye());
        camt056.put("endToEndId", request.getEndToEndId());
        camt056.put("raison", request.getRaison().name());

        messageLogService.log(msgId, request.getEndToEndId(), IsoMessageType.CAMT_056, MessageDirection.OUTBOUND, camt056, null, null);

        PiReturnRequest returnReq = PiReturnRequest.builder()
                .msgId(msgId)
                .identifiantDemande(msgId)
                .endToEndId(request.getEndToEndId())
                .direction(MessageDirection.OUTBOUND)
                .codeMembrePayeur(codeMembre)
                .codeMembrePaye(request.getCodeMembreParticipantPaye())
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

        // Spec §4.8.2 CAMT.029: msgId, msgIdDemande, codeMembreParticipantPayeur, statut, endToEndId, raison
        Map<String, Object> camt029 = new HashMap<>();
        camt029.put("msgId", msgId);
        camt029.put("msgIdDemande", returnReq.getMsgId());
        camt029.put("codeMembreParticipantPayeur", returnReq.getCodeMembrePayeur());
        camt029.put("statut", "RJCR");
        camt029.put("endToEndId", returnReq.getEndToEndId());
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

        // Garde idempotence : on ne ré-émet pas un PACS.004 si la demande
        // est déjà ACCEPTED (race avec un retry du backend) ou RJCR.
        if (returnReq.getStatut() != ReturnRequestStatus.PENDING) {
            log.warn("acceptReturn ignoré : demande déjà finalisée "
                    + "[identifiantDemande={}, statut={}]",
                    identifiantDemande, returnReq.getStatut());
            ReturnFundsResponse resp = toResponse(returnReq);
            return resp;
        }

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        // Spec §4.8.3 PACS.004: endToEndId must identify the original transfer being returned
        String endToEndId = returnReq.getEndToEndId();

        Map<String, Object> pacs004 = new HashMap<>();
        pacs004.put("msgId", msgId);
        pacs004.put("endToEndId", endToEndId);
        pacs004.put("montantRetourne", request.getMontantRetourne());
        pacs004.put("raisonRetour", request.getRaisonRetour().name());

        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_004, MessageDirection.OUTBOUND, pacs004, null, null);
        aipClient.post("/retour-fonds", pacs004);

        // Audit trail : PiReturnExecution OUTBOUND lie le PACS.004 à la
        // PiReturnRequest INBOUND d'origine via {@code returnRequestId}.
        // C'est cette ligne qui est consultée en B1 (auto-rejet ARDT) pour
        // détecter qu'un retour a déjà été exécuté pour cet endToEndId.
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

        // Bascule du PiTransfer original vers RTND. Comme nous sommes le payé
        // qui rend les fonds (on émet PACS.004 OUTBOUND), la ligne à mettre à
        // jour est l'INBOUND (PACS.008 reçu à l'origine). Le code raisonRetour
        // est stocké dans codeRaison pour traçabilité (CUST si décision client,
        // FR01/AC06/etc. si retour technique).
        //
        // Sans cette bascule, le transfer afficherait toujours ACCC alors que
        // les fonds ont été retournés — incohérence avec PiReturnExecution.
        transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.INBOUND)
                .ifPresent(transfer -> {
                    if (transfer.getStatut() != null && transfer.getStatut().isTerminal()
                            && transfer.getStatut() != TransferStatus.RTND) {
                        log.warn("acceptReturn : transfer INBOUND déjà terminal — "
                                + "pas d'écrasement [endToEndId={}, statut={}]",
                                endToEndId, transfer.getStatut());
                        return;
                    }
                    TransferStatus previous = transfer.getStatut();
                    transfer.setStatut(TransferStatus.RTND);
                    transfer.setCodeRaison(request.getRaisonRetour().name());
                    transferRepository.save(transfer);
                    log.info("Transfer INBOUND {} → RTND via acceptReturn "
                            + "[précédent={}, raisonRetour={}]",
                            endToEndId, previous, request.getRaisonRetour());
                });

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
