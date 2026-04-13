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

import static ci.sycapay.pispi.util.DateTimeUtil.formatDateTime;

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
                .typeTransaction(request.getTypeTransaction())
                .canalCommunication(request.getCanalCommunication())
                .montant(request.getMontant())
                .devise("XOF")
                .codeMembrePayeur(codeMembre)
                .codeMembrePaye(request.getCodeMembreParticipantPaye())
                .numeroComptePayeur(request.getNumeroCompteClientPayeur())
                .typeComptePayeur(request.getTypeCompteClientPayeur())
                .numeroComptePaye(request.getNumeroCompteClientPaye())
                .typeComptePaye(request.getTypeCompteClientPaye())
                .nomClientPayeur(request.getClientPayeur().getNom())
                .prenomClientPayeur(request.getClientPayeur().getPrenom())
                .nomClientPaye(request.getClientPaye().getNom())
                .prenomClientPaye(request.getClientPaye().getPrenom())
                .motif(request.getMotif())
                .referenceClient(request.getReferenceClient())
                .statut(TransferStatus.PEND)
                .build();
        transferRepository.save(transfer);

        aipClient.post("/transferts", aipPayload);

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
        aipClient.post("/transferts/reponses", pacs002);

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
        aipClient.post("/transferts/statuts", pacs028);
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
        payload.put("clientPayeur", buildClientMap(request.getClientPayeur()));
        payload.put("clientPaye", buildClientMap(request.getClientPaye()));
        if (request.getDateHeureExecution() != null) payload.put("dateHeureExecution", request.getDateHeureExecution());
        if (request.getMotif() != null) payload.put("motif", request.getMotif());
        if (request.getReferenceClient() != null) payload.put("referenceClient", request.getReferenceClient());
        if (request.getDocument() != null) {
            Map<String, Object> doc = new HashMap<>();
            if (request.getDocument().getCodeTypeDocument() != null) doc.put("codeTypeDocument", request.getDocument().getCodeTypeDocument().name());
            if (request.getDocument().getIdentifiantDocument() != null) doc.put("identifiantDocument", request.getDocument().getIdentifiantDocument());
            if (request.getDocument().getLibelleDocument() != null) doc.put("libelleDocument", request.getDocument().getLibelleDocument());
            payload.put("document", doc);
        }
        if (request.getMarchand() != null) payload.put("marchand", buildMarchandMap(request.getMarchand()));
        if (request.getMontantAchat() != null) payload.put("montantAchat", request.getMontantAchat());
        if (request.getMontantRetrait() != null) payload.put("montantRetrait", request.getMontantRetrait());
        if (request.getFraisRetrait() != null) payload.put("fraisRetrait", request.getFraisRetrait());
        return payload;
    }

    private Map<String, Object> buildClientMap(ci.sycapay.pispi.dto.common.ClientInfo client) {
        Map<String, Object> map = new HashMap<>();
        map.put("nom", client.getNom());
        map.put("typeClient", client.getTypeClient().name());
        map.put("typeIdentifiant", client.getTypeIdentifiant().name());
        map.put("identifiant", client.getIdentifiant());
        map.put("telephone", client.getTelephone());
        if (client.getPrenom() != null) map.put("prenom", client.getPrenom());
        if (client.getAutrePrenom() != null) map.put("autrePrenom", client.getAutrePrenom());
        if (client.getRaisonSociale() != null) map.put("raisonSociale", client.getRaisonSociale());
        if (client.getDateNaissance() != null) map.put("dateNaissance", client.getDateNaissance());
        if (client.getLieuNaissance() != null) map.put("lieuNaissance", client.getLieuNaissance());
        if (client.getNationalite() != null) map.put("nationalite", client.getNationalite());
        if (client.getAdresse() != null) map.put("adresse", client.getAdresse());
        if (client.getVille() != null) map.put("ville", client.getVille());
        if (client.getPays() != null) map.put("pays", client.getPays());
        if (client.getEmail() != null) map.put("email", client.getEmail());
        return map;
    }

    private Map<String, Object> buildMarchandMap(ci.sycapay.pispi.dto.common.MerchantInfo marchand) {
        Map<String, Object> map = new HashMap<>();
        if (marchand.getCodeMarchand() != null) map.put("codeMarchand", marchand.getCodeMarchand());
        if (marchand.getCategorieCodeMarchand() != null) map.put("categorieCodeMarchand", marchand.getCategorieCodeMarchand());
        if (marchand.getNomMarchand() != null) map.put("nomMarchand", marchand.getNomMarchand());
        if (marchand.getVilleMarchand() != null) map.put("villeMarchand", marchand.getVilleMarchand());
        if (marchand.getPaysMarchand() != null) map.put("paysMarchand", marchand.getPaysMarchand());
        return map;
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
                .dateHeureIrrevocabilite(formatDateTime(t.getDateHeureIrrevocabilite()))
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .build();
    }
}
