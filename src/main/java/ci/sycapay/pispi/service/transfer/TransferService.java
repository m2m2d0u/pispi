package ci.sycapay.pispi.service.transfer;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.transfer.TransferAcceptRejectRequest;
import ci.sycapay.pispi.dto.transfer.TransferRequest;
import ci.sycapay.pispi.dto.transfer.TransferResponse;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TransferStatus;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final PiTransferRepository transferRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> aipPayload = buildPacs008Payload(msgId, endToEndId, request);
        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_008, MessageDirection.OUTBOUND, aipPayload, null, null);

        PiTransfer transfer = PiTransfer.builder()
                .msgId(msgId)
                .endToEndId(endToEndId)
                .direction(MessageDirection.OUTBOUND)
                .typeTransaction(request.getTypeTransaction().name())
                .canalCommunication(request.getCanalCommunication().getCode())
                .montant(request.getMontant())
                .devise("XOF")
                .codeMembrePayeur(codeMembre)
                .codeMembrePaye(request.getCodeMembreParticipantPaye())
                .numeroComptePayeur(request.getNumeroCompteClientPayeur())
                .typeComptePayeur(request.getTypeCompteClientPayeur().name())
                .numeroComptePaye(request.getNumeroCompteClientPaye())
                .typeComptePaye(request.getTypeCompteClientPaye().name())
                .nomClientPayeur(request.getClientPayeur().getNom())
                .prenomClientPayeur(request.getClientPayeur().getPrenom())
                .nomClientPaye(request.getClientPaye().getNom())
                .prenomClientPaye(request.getClientPaye().getPrenom())
                .motif(request.getMotif())
                .referenceClient(request.getReferenceClient())
                .statut(TransferStatus.PEND)
                .build();
        transferRepository.save(transfer);

        aipClient.post("/api/spi/v{version}/virement", aipPayload);

        return toResponse(transfer);
    }

    public TransferResponse getTransfer(String endToEndId) {
        PiTransfer transfer = transferRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", endToEndId));
        return toResponse(transfer);
    }

    public Page<TransferResponse> listTransfers(Pageable pageable) {
        return transferRepository.findByDirection(MessageDirection.OUTBOUND, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public TransferResponse acceptOrReject(String endToEndId, TransferAcceptRejectRequest request) {
        PiTransfer transfer = transferRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", endToEndId));

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> pacs002 = new HashMap<>();
        pacs002.put("msgId", msgId);
        pacs002.put("msgIdDemande", transfer.getMsgId());
        pacs002.put("endToEndId", endToEndId);
        pacs002.put("statutTransaction", request.getStatutTransaction().name());
        if (request.getCodeRaison() != null) {
            pacs002.put("codeRaison", request.getCodeRaison());
        }

        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_002, MessageDirection.OUTBOUND, pacs002, null, null);
        aipClient.post("/api/spi/v{version}/virement/reponse", pacs002);

        transfer.setStatut(TransferStatus.valueOf(request.getStatutTransaction().name()));
        transfer.setCodeRaison(request.getCodeRaison());
        transfer.setMsgIdReponse(msgId);
        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    public void queryStatus(String endToEndId) {
        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);

        Map<String, Object> pacs028 = new HashMap<>();
        pacs028.put("msgId", msgId);
        pacs028.put("endToEndId", endToEndId);

        messageLogService.log(msgId, endToEndId, IsoMessageType.PACS_028, MessageDirection.OUTBOUND, pacs028, null, null);
        aipClient.post("/api/spi/v{version}/virement/statut", pacs028);
    }

    private Map<String, Object> buildPacs008Payload(String msgId, String endToEndId, TransferRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("msgId", msgId);
        payload.put("endToEndId", endToEndId);
        payload.put("typeTransaction", request.getTypeTransaction().name());
        payload.put("canalCommunication", request.getCanalCommunication().getCode());
        payload.put("montant", request.getMontant());
        payload.put("devise", "XOF");
        payload.put("codeMembreParticipantPayeur", properties.getCodeMembre());
        payload.put("codeMembreParticipantPaye", request.getCodeMembreParticipantPaye());
        payload.put("numeroCompteClientPayeur", request.getNumeroCompteClientPayeur());
        payload.put("typeCompteClientPayeur", request.getTypeCompteClientPayeur().name());
        payload.put("numeroCompteClientPaye", request.getNumeroCompteClientPaye());
        payload.put("typeCompteClientPaye", request.getTypeCompteClientPaye().name());
        if (request.getDateHeureExecution() != null) payload.put("dateHeureExecution", request.getDateHeureExecution());
        if (request.getMotif() != null) payload.put("motif", request.getMotif());
        if (request.getReferenceClient() != null) payload.put("referenceClient", request.getReferenceClient());
        return payload;
    }

    private TransferResponse toResponse(PiTransfer t) {
        return TransferResponse.builder()
                .endToEndId(t.getEndToEndId())
                .msgId(t.getMsgId())
                .statut(t.getStatut())
                .codeRaison(t.getCodeRaison())
                .montant(t.getMontant())
                .codeMembreParticipantPayeur(t.getCodeMembrePayeur())
                .codeMembreParticipantPaye(t.getCodeMembrePaye())
                .dateHeureIrrevocabilite(t.getDateHeureIrrevocabilite())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .build();
    }
}
