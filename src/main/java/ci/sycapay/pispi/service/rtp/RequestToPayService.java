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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Slf4j
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
        pain013.put("numeroCompteClientPayeur", request.getNumeroCompteClientPayeur());
        pain013.put("typeCompteClientPayeur", request.getTypeCompteClientPayeur().name());
        pain013.put("numeroCompteClientPaye", request.getNumeroCompteClientPaye());
        pain013.put("typeCompteClientPaye", request.getTypeCompteClientPaye().name());
        pain013.put("clientPayeur", buildClientMap(request.getClientPayeur()));
        pain013.put("clientPaye", buildClientMap(request.getClientPaye()));
        if (request.getDateHeureExecution() != null) pain013.put("dateHeureExecution", request.getDateHeureExecution());
        if (request.getDateHeureLimiteAction() != null) pain013.put("dateHeureLimiteAction", request.getDateHeureLimiteAction());
        if (request.getMotif() != null) pain013.put("motif", request.getMotif());
        if (request.getReferenceClient() != null) pain013.put("referenceClient", request.getReferenceClient());
        if (request.getAutorisationModificationMontant() != null) pain013.put("autorisationModificationMontant", request.getAutorisationModificationMontant());
        if (request.getMontantRemisePaiementImmediat() != null) pain013.put("montantRemisePaiementImmediat", request.getMontantRemisePaiementImmediat());
        if (request.getTauxRemisePaiementImmediat() != null) pain013.put("tauxRemisePaiementImmediat", request.getTauxRemisePaiementImmediat());
        if (request.getIdentifiantMandat() != null) pain013.put("identifiantMandat", request.getIdentifiantMandat());
        if (request.getSignatureNumeriqueMandat() != null) pain013.put("signatureNumeriqueMandat", request.getSignatureNumeriqueMandat());
        if (request.getDocument() != null) {
            Map<String, Object> doc = new HashMap<>();
            if (request.getDocument().getCodeTypeDocument() != null) doc.put("codeTypeDocument", request.getDocument().getCodeTypeDocument().name());
            if (request.getDocument().getIdentifiantDocument() != null) doc.put("identifiantDocument", request.getDocument().getIdentifiantDocument());
            if (request.getDocument().getLibelleDocument() != null) doc.put("libelleDocument", request.getDocument().getLibelleDocument());
            pain013.put("document", doc);
        }
        if (request.getMarchand() != null) pain013.put("marchand", buildMarchandMap(request.getMarchand()));
        if (request.getMontantAchat() != null) pain013.put("montantAchat", request.getMontantAchat());
        if (request.getMontantRetrait() != null) pain013.put("montantRetrait", request.getMontantRetrait());
        if (request.getFraisRetrait() != null) pain013.put("fraisRetrait", request.getFraisRetrait());

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
                .dateHeureLimiteAction(parseDateTime(request.getDateHeureLimiteAction()))
                .autorisationModificationMontant(request.getAutorisationModificationMontant())
                .statut(RtpStatus.PENDING)
                .build();
        rtpRepository.save(rtp);

        aipClient.post("/demandes-paiement", pain013);

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
        aipClient.post("/demandes-paiement/reponses", pain014);

        rtp.setStatut(RtpStatus.RJCT);
        rtp.setCodeRaison(codeRaison);
        rtp.setMsgIdReponse(msgId);
        rtpRepository.save(rtp);

        return toResponse(rtp);
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
