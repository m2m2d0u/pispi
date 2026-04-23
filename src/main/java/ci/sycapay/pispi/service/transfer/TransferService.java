package ci.sycapay.pispi.service.transfer;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.transfer.TransferAcceptRejectRequest;
import ci.sycapay.pispi.dto.transfer.TransferRequest;
import ci.sycapay.pispi.dto.transfer.TransferResponse;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.CanalCommunication;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TransferStatus;
import ci.sycapay.pispi.enums.TypeTransaction;
import java.time.LocalDateTime;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.resolver.ClientSearchResolver;
import ci.sycapay.pispi.service.resolver.ResolvedClient;
import ci.sycapay.pispi.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static ci.sycapay.pispi.util.DateTimeUtil.formatDateTime;
import static ci.sycapay.pispi.util.DateTimeUtil.nowIso;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final PiTransferRepository transferRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final ClientSearchResolver clientSearchResolver;

    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request) {
        if (request.getTypeTransaction() == TypeTransaction.DISP
                && request.getMontant().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Pour un virement DISP (disponibilité), le montant doit être 1 franc CFA. " +
                    "Le montant réel du retrait doit être renseigné dans montantRetrait.");
        }

        ResolvedClient payeur = clientSearchResolver.resolve(request.getEndToEndIdSearchPayeur(), "payeur");

        // BCEAO rule: for DISP, payeur and paye must be the same person
        ResolvedClient paye = request.getTypeTransaction() == TypeTransaction.DISP
                ? payeur
                : clientSearchResolver.resolve(request.getEndToEndIdSearchPaye(), "paye");

        validateLocalisationRules(request.getCanalCommunication(), request);

        String codeMembre = properties.getCodeMembre();
        String msgId = IdGenerator.generateMsgId(codeMembre);
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> aipPayload = buildPacs008Payload(msgId, endToEndId, request, payeur, paye);
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
                .codeMembrePaye(paye.codeMembre())
                .numeroComptePayeur(payeur.other())
                .typeComptePayeur(payeur.typeCompte())
                .typeClientPayeur(payeur.clientInfo().getTypeClient())
                .nomClientPayeur(payeur.clientInfo().getNom())
                .prenomClientPayeur(payeur.clientInfo().getPrenom())
                .telephonePayeur(payeur.clientInfo().getTelephone())
                .numeroComptePaye(paye.other())
                .typeComptePaye(paye.typeCompte())
                .typeClientPaye(paye.clientInfo().getTypeClient())
                .nomClientPaye(paye.clientInfo().getNom())
                .prenomClientPaye(paye.clientInfo().getPrenom())
                .telephonePaye(paye.clientInfo().getTelephone())
                .motif(request.getMotif())
                .referenceClient(request.getIdentifiantTransaction())
                .montantAchat(request.getMontantAchat())
                .montantRetrait(request.getMontantRetrait())
                .fraisRetrait(request.getFraisRetrait())
                .dateHeureExecution(LocalDateTime.now())
                .statut(TransferStatus.PEND)
                .build();
        transferRepository.save(transfer);
        log.info("Transfer initiated with payload: {}", aipPayload);
        aipClient.post("/transferts", aipPayload);

        return toResponse(transfer);
    }

    public TransferResponse getTransfer(String endToEndId) {
        PiTransfer transfer = transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", endToEndId));
        return toResponse(transfer);
    }

    public Page<TransferResponse> listTransfers(Pageable pageable) {
        return transferRepository.findByDirection(MessageDirection.OUTBOUND, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public TransferResponse acceptOrReject(String endToEndId, TransferAcceptRejectRequest request) {
        PiTransfer transfer = transferRepository.findByEndToEndIdAndDirection(endToEndId, MessageDirection.OUTBOUND)
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
        aipClient.post("/transferts/statut", pacs028);
    }

    // -------------------------------------------------------------------------
    // Pre-send BCEAO validation
    // -------------------------------------------------------------------------

    private static final Set<CanalCommunication> CANALS_REQUIRING_LOCALISATION =
            Set.of(
                    CanalCommunication.QR_CODE, CanalCommunication.ADRESSE_PAIEMENT,
                    CanalCommunication.QR_CODE_STATIQUE, CanalCommunication.QR_CODE_DYNAMIQUE,
                    CanalCommunication.FACTURE, CanalCommunication.MARCHAND_SUR_SITE,
                    CanalCommunication.E_COMMERCE_LIVRAISON, CanalCommunication.E_COMMERCE_IMMEDIAT,
                    CanalCommunication.PARTICULIER);

    private static final Set<CanalCommunication> CANALS_REQUIRING_IDENTIFIANT_TRANSACTION =
            Set.of(
                    CanalCommunication.QR_CODE_DYNAMIQUE, CanalCommunication.API_BUSINESS,
                    CanalCommunication.MARCHAND_SUR_SITE, CanalCommunication.E_COMMERCE_IMMEDIAT,
                    CanalCommunication.E_COMMERCE_LIVRAISON, CanalCommunication.PARTICULIER,
                    CanalCommunication.FACTURE);

    private void validateLocalisationRules(CanalCommunication canal, TransferRequest request) {
        // GPS coordinates (latitude + longitude) are required by the AIP for physical/QR/e-commerce channels
        if (CANALS_REQUIRING_LOCALISATION.contains(canal)) {
            if (request.getLatitudePayeur() == null || request.getLatitudePayeur().isBlank()
                    || request.getLongitudePayeur() == null || request.getLongitudePayeur().isBlank()) {
                throw new IllegalArgumentException(
                        "La localisation GPS (latitudePayeur, longitudePayeur) est obligatoire pour le canal " + canal.name());
            }
        }
        // identifiantTransaction is required by the AIP for channels 400, 733, 500, 521, 520, 631, 401
        if (CANALS_REQUIRING_IDENTIFIANT_TRANSACTION.contains(canal)) {
            if (request.getIdentifiantTransaction() == null || request.getIdentifiantTransaction().isBlank()) {
                throw new IllegalArgumentException(
                        "identifiantTransaction est obligatoire pour le canal " + canal.name());
            }
        }
    }

    // -------------------------------------------------------------------------
    // PACS.008 payload builder
    // -------------------------------------------------------------------------

    private Map<String, Object> buildPacs008Payload(String msgId, String endToEndId,
                                                     TransferRequest request,
                                                     ResolvedClient payeur,
                                                     ResolvedClient paye) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("msgId", msgId);
        payload.put("endToEndId", endToEndId);

        payload.put("canalCommunication", request.getCanalCommunication().getCode());
        payload.put("codeMembreParticipantPayeur", properties.getCodeMembre());
        payload.put("codeMembreParticipantPaye", paye.codeMembre());
        // BCEAO rule: DISP (disponibilité) messages must carry montant = 1; real amount goes in montantRetrait.
        String montantAip = request.getMontant().toBigInteger().toString();
        payload.put("montant", montantAip);
        payload.put("dateHeureAcceptation", nowIso());

        // Payeur — required
        payload.put("nomClientPayeur", payeur.clientInfo().getNom());
        payload.put("typeClientPayeur", payeur.clientInfo().getTypeClient().name());
        payload.put("paysClientPayeur", payeur.clientInfo().getPays());
        payload.put("typeCompteClientPayeur", payeur.typeCompte().name());
        payload.put("deviseCompteClientPayeur", "XOF");
        // IBAN vs other: exactly one should be non-null per RAC_SEARCH spec §4.1.4.2.
        // Sending the wrong field (or null) is the primary trigger of BE01.
        if (payeur.iban() != null) {
            payload.put("ibanClientPayeur", payeur.iban());
        } else if (payeur.other() != null) {
            payload.put("otherClientPayeur", payeur.other());
        }

        // Payeur — optional
        if (payeur.clientInfo().getAdresse() != null)
            payload.put("adresseClientPayeur", payeur.clientInfo().getAdresse());
        if (payeur.clientInfo().getVille() != null)
            payload.put("villeClientPayeur", payeur.clientInfo().getVille());
        if (payeur.clientInfo().getDateNaissance() != null
                && payeur.clientInfo().getLieuNaissance() != null
                && payeur.clientInfo().getPaysNaissance() != null) {
            payload.put("dateNaissanceClientPayeur", payeur.clientInfo().getDateNaissance());
            payload.put("villeNaissanceClientPayeur", payeur.clientInfo().getLieuNaissance());
            payload.put("paysNaissanceClientPayeur", payeur.clientInfo().getPaysNaissance());
        }
        if (payeur.clientInfo().getTypeIdentifiant() != null)
            payload.put("systemeIdentificationClientPayeur", payeur.clientInfo().getTypeIdentifiant().name());
        if (payeur.clientInfo().getIdentifiant() != null)
            payload.put("numeroIdentificationClientPayeur", payeur.clientInfo().getIdentifiant());
        if (payeur.aliasValue() != null)
            payload.put("aliasClientPayeur", payeur.aliasValue());
        // Type C: commercial identifiers required when provided at alias enrollment
        if (payeur.identificationFiscaleCommercant() != null)
            payload.put("identificationFiscaleCommercantPayeur", payeur.identificationFiscaleCommercant());
        else if (payeur.identificationRccm() != null)
            payload.put("numeroRCCMClientPayeur", payeur.identificationRccm());

        // Payé — required
        payload.put("nomClientPaye", paye.clientInfo().getNom());
        payload.put("typeClientPaye", paye.clientInfo().getTypeClient().name());
        payload.put("paysClientPaye", paye.clientInfo().getPays());
        payload.put("typeCompteClientPaye", paye.typeCompte().name());
        payload.put("deviseCompteClientPaye", "XOF");
        if (paye.iban() != null) {
            payload.put("ibanClientPaye", paye.iban());
        } else if (paye.other() != null) {
            payload.put("otherClientPaye", paye.other());
        }

        // Payé — optional
        if (paye.clientInfo().getAdresse() != null)
            payload.put("adresseClientPaye", paye.clientInfo().getAdresse());
        if (paye.clientInfo().getVille() != null)
            payload.put("villeClientPaye", paye.clientInfo().getVille());
        if (paye.clientInfo().getDateNaissance() != null
                && paye.clientInfo().getLieuNaissance() != null
                && paye.clientInfo().getPaysNaissance() != null) {
            payload.put("dateNaissanceClientPaye", paye.clientInfo().getDateNaissance());
            payload.put("villeNaissanceClientPaye", paye.clientInfo().getLieuNaissance());
            payload.put("paysNaissanceClientPaye", paye.clientInfo().getPaysNaissance());
        }
        if (paye.clientInfo().getTypeIdentifiant() != null)
            payload.put("systemeIdentificationClientPaye", paye.clientInfo().getTypeIdentifiant().name());
        if (paye.clientInfo().getIdentifiant() != null)
            payload.put("numeroIdentificationClientPaye", paye.clientInfo().getIdentifiant());
        if (paye.aliasValue() != null)
            payload.put("aliasClientPaye", paye.aliasValue());
        // Type C: commercial identifiers required when provided at alias enrollment
        if (paye.identificationFiscaleCommercant() != null)
            payload.put("identificationFiscaleCommercantPaye", paye.identificationFiscaleCommercant());
        else if (paye.identificationRccm() != null)
            payload.put("numeroRCCMClientPaye", paye.identificationRccm());

        // Optional transfer fields
        if (request.getTypeTransaction() != null)
            payload.put("typeTransaction", request.getTypeTransaction().name());
        if (request.getMotif() != null)
            payload.put("motif", request.getMotif());
        if (request.getIdentifiantTransaction() != null)
            payload.put("identifiantTransaction", request.getIdentifiantTransaction());
        if (request.getReferenceBulk() != null)
            payload.put("referenceBulk", request.getReferenceBulk());

        // montantAchat and document omitted: AIP maps them to RmtInf/Strd which conflicts
        // with motif → RmtInf/Ustrd. PI SPI rejects PACS.008.001.10 messages containing Strd.
        if (request.getLatitudePayeur() != null)
            payload.put("latitudeClientPayeur", request.getLatitudePayeur());
        if (request.getLongitudePayeur() != null)
            payload.put("longitudeClientPayeur", request.getLongitudePayeur());

        if (request.getMontantRetrait() != null)
            payload.put("montantRetrait", request.getMontantRetrait().toBigInteger().toString());
        if (request.getFraisRetrait() != null)
            payload.put("fraisRetrait", request.getFraisRetrait().toBigInteger().toString());

        return payload;
    }

    private TransferResponse toResponse(PiTransfer t) {
        return TransferResponse.builder()
                .endToEndId(t.getEndToEndId())
                .msgId(t.getMsgId())
                .statut(t.getStatut())
                .codeRaison(t.getCodeRaison())
                .detailEchec(t.getDetailEchec())
                .montant(t.getMontant())
                .codeMembreParticipantPayeur(t.getCodeMembrePayeur())
                .codeMembreParticipantPaye(t.getCodeMembrePaye())
                .dateHeureIrrevocabilite(formatDateTime(t.getDateHeureIrrevocabilite()))
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .build();
    }

}
