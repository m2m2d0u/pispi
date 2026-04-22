package ci.sycapay.pispi.service.transfer;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.dto.transfer.TransferAcceptRejectRequest;
import ci.sycapay.pispi.dto.transfer.TransferRequest;
import ci.sycapay.pispi.dto.transfer.TransferResponse;
import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.CanalCommunication;
import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.TransferStatus;
import ci.sycapay.pispi.enums.TypeTransaction;
import java.time.LocalDateTime;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.repository.PiMessageLogRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.util.IdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final PiMessageLogRepository messageLogRepository;
    private final PiAliasRepository aliasRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request) {
        if (request.getTypeTransaction() == TypeTransaction.DISP
                && request.getMontant().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Pour un virement DISP (disponibilité), le montant doit être 1 franc CFA. " +
                    "Le montant réel du retrait doit être renseigné dans montantRetrait.");
        }

        ResolvedClient payeur = resolveClientFromSearchLog(request.getEndToEndIdSearchPayeur(), "payeur");

        // BCEAO rule: for DISP, payeur and paye must be the same person
        ResolvedClient paye = request.getTypeTransaction() == TypeTransaction.DISP
                ? payeur
                : resolveClientFromCodification(request.getCodificationPaye(), "paye");

        validateLocalisationRules(request.getCanalCommunication(), payeur, paye);

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

    private void validateLocalisationRules(CanalCommunication canal, ResolvedClient payeur, ResolvedClient paye) {
        log.info("Validate the localisation rules");
        // Rule 1: ville required for B/G/C type clients
        for (var entry : new Object[][]{{payeur, "payeur"}, {paye, "paye"}}) {
            ResolvedClient client = (ResolvedClient) entry[0];
            String side = (String) entry[1];
            TypeClient type = client.clientInfo().getTypeClient();
            if ((type == TypeClient.B || type == TypeClient.G || type == TypeClient.C)
                    && (client.clientInfo().getVille() == null || client.clientInfo().getVille().isBlank())) {
                throw new IllegalArgumentException(
                        "La ville est obligatoire pour le " + side + " de type " + type.name());
            }
        }
        // Rule 2: villeClientPayeur required for merchant/QR/e-commerce channels
        if (CANALS_REQUIRING_LOCALISATION.contains(canal)
                && (payeur.clientInfo().getVille() == null || payeur.clientInfo().getVille().isBlank())) {
            throw new IllegalArgumentException(
                    "La localisation (ville) du client payeur est obligatoire pour le canal " + canal.name());
        }
    }

    // -------------------------------------------------------------------------
    // Client resolution
    // -------------------------------------------------------------------------

    private ResolvedClient resolveClientFromCodification(String codification, String side) {
        PiAlias alias = aliasRepository.findByCodification(codification)
                .orElseThrow(() -> new ResourceNotFoundException("Alias", codification));

        if (alias.getNumeroCompte() == null || alias.getTypeCompte() == null) {
            throw new IllegalStateException(
                    "Alias " + codification + " incomplet : numeroCompte ou typeCompte manquant");
        }

        ClientInfo.ClientInfoBuilder builder = ClientInfo.builder()
                .nom(alias.getNom())
                .typeClient(alias.getTypeClient())
                .pays(alias.getPays())
                .telephone(alias.getTelephone());

        if (alias.getNationalite() != null) builder.nationalite(alias.getNationalite());
        if (alias.getAdresse() != null) builder.adresse(alias.getAdresse());
        if (alias.getVille() != null) builder.ville(alias.getVille());
        if (alias.getDateNaissance() != null) builder.dateNaissance(alias.getDateNaissance().toString());
        if (alias.getLieuNaissance() != null) builder.lieuNaissance(alias.getLieuNaissance());
        if (alias.getPaysNaissance() != null) builder.paysNaissance(alias.getPaysNaissance());
        if (alias.getIdentifiant() != null) {
            builder.identifiant(alias.getIdentifiant())
                   .typeIdentifiant(alias.getTypeIdentifiant());
        }
        if (alias.getEmail() != null) builder.email(alias.getEmail());

        log.info("Client {} résolu depuis la codification [{}]", side, codification);
        return new ResolvedClient(builder.build(), alias.getNumeroCompte(), alias.getTypeCompte(), alias.getAliasValue(), alias.getCodeMembreParticipant());
    }

    private ResolvedClient resolveClientFromSearchLog(String endToEndIdSearch, String side) {
        PiMessageLog entry = messageLogRepository
                .findFirstByEndToEndIdAndDirectionAndMessageTypeOrderByCreatedAtDesc(
                        endToEndIdSearch, MessageDirection.INBOUND, IsoMessageType.RAC_SEARCH)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Résultat de recherche d'alias introuvable pour endToEndId", endToEndIdSearch));

        Map<String, Object> data;
        try {
            data = objectMapper.readValue(entry.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de lire le payload du log [endToEndId=" + endToEndIdSearch + "]", e);
        }

        String nom = getString(data, "nom");
        String telephone = getString(data, "telephone");
        String paysResidence = getString(data, "paysResidence");
        String categorie = getString(data, "categorie");
        String other = getString(data, "other");
        String typeCompteStr = getString(data, "typeCompte");

        if (nom == null || telephone == null || paysResidence == null || categorie == null
                || other == null || typeCompteStr == null) {
            throw new IllegalStateException(
                    "Payload du résultat de recherche incomplet pour le " + side + " [endToEndId=" + endToEndIdSearch + "]");
        }

        TypeClient typeClient;
        try {
            typeClient = TypeClient.valueOf(categorie);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Catégorie client inconnue: " + categorie);
        }

        TypeCompte typeCompte;
        try {
            typeCompte = TypeCompte.valueOf(typeCompteStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Type de compte inconnu: " + typeCompteStr);
        }

        ClientInfo.ClientInfoBuilder builder = ClientInfo.builder()
                .nom(nom)
                .typeClient(typeClient)
                .pays(paysResidence)
                .telephone(telephone);

        String genre = getString(data, "genre");
        if (genre != null) builder.genre(genre);

        String adresse = getString(data, "adresse");
        if (adresse != null) builder.adresse(adresse);

        String ville = getString(data, "ville");
        if (ville != null) builder.ville(ville);

        String dateNaissance = getString(data, "dateNaissance");
        if (dateNaissance != null) builder.dateNaissance(dateNaissance);

        String paysNaissance = getString(data, "paysNaissance");
        if (paysNaissance != null) builder.paysNaissance(paysNaissance);

        String villeNaissance = getString(data, "villeNaissance");
        if (villeNaissance != null) builder.lieuNaissance(villeNaissance);

        String nationalite = getString(data, "nationalite");
        if (nationalite != null) builder.nationalite(nationalite);

        String identificationNationale = getString(data, "identificationNationale");
        if (identificationNationale != null) {
            builder.identifiant(identificationNationale)
                   .typeIdentifiant(CodeSystemeIdentification.NIDN);
        }

        String email = getString(data, "email");
        if (email != null) builder.email(email);

        String valeurAlias = getString(data, "valeurAlias");
        String codeMembreParticipant = getString(data, "participant");

        log.info("Client {} résolu depuis le log de recherche [endToEndId={}]", side, endToEndIdSearch);
        return new ResolvedClient(builder.build(), other, typeCompte, valeurAlias, codeMembreParticipant);
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : null;
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
        payload.put("otherClientPayeur", payeur.other());

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

        // Payé — required
        payload.put("nomClientPaye", paye.clientInfo().getNom());
        payload.put("typeClientPaye", paye.clientInfo().getTypeClient().name());
        payload.put("paysClientPaye", paye.clientInfo().getPays());
        payload.put("typeCompteClientPaye", paye.typeCompte().name());
        payload.put("deviseCompteClientPaye", "XOF");
        payload.put("otherClientPaye", paye.other());

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

    /** Holds the resolved client data ready to use in the PACS.008 payload. */
    private record ResolvedClient(ClientInfo clientInfo, String other, TypeCompte typeCompte, String aliasValue, String codeMembre) {}
}
