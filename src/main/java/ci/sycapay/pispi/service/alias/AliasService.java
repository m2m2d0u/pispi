package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.alias.AliasCreationRequest;
import ci.sycapay.pispi.dto.alias.AliasResponse;
import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRepository;
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
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliasService {

    private final PiAliasRepository aliasRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final MessageLogService messageLogService;

    @Transactional
    public AliasResponse createAlias(AliasCreationRequest request) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> payload = buildAliasPayload(endToEndId, request);
        messageLogService.log(null, endToEndId, IsoMessageType.RAC_CREATE, MessageDirection.OUTBOUND, payload, null, null);

        Map<String, Object> response = aipClient.post("/alias/creation", payload);

        PiAlias alias = PiAlias.builder()
                .endToEndId(endToEndId)
                .aliasValue(request.getAlias())
                .typeAlias(request.getTypeAlias())
                .typeClient(request.getClient().getTypeClient())
                .nom(request.getClient().getNom())
                .prenom(request.getClient().getPrenom())
                .telephone(request.getClient().getTelephone())
                .email(request.getClient().getEmail())
                .numeroCompte(request.getNumeroCompte())
                .typeCompte(request.getTypeCompte())
                .codeMembreParticipant(codeMembre)
                .statut(AliasStatus.ACTIVE)
                .dateCreationRac(response != null && response.get("dateCreation") != null
                        ? parseDateTime(String.valueOf(response.get("dateCreation"))) : null)
                .build();

        if (request.getMarchand() != null) {
            alias.setCodeMarchand(request.getMarchand().getCodeMarchand());
            alias.setCategorieCodeMarchand(request.getMarchand().getCategorieCodeMarchand());
            alias.setNomMarchand(request.getMarchand().getNomMarchand());
        }

        aliasRepository.save(alias);

        return AliasResponse.builder()
                .endToEndId(endToEndId)
                .statut(StatutOperationAlias.SUCCES)
                .alias(request.getAlias())
                .typeAlias(request.getTypeAlias())
                .dateCreation(formatDateTime(alias.getDateCreationRac()))
                .build();
    }

    @Transactional
    public AliasResponse modifyAlias(AliasCreationRequest request) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> payload = buildAliasPayload(endToEndId, request);
        messageLogService.log(null, endToEndId, IsoMessageType.RAC_MODIFY, MessageDirection.OUTBOUND, payload, null, null);

        Map<String, Object> response = aipClient.post("/alias/modification", payload);

        PiAlias alias = aliasRepository.findByAliasValueAndTypeAliasAndStatut(
                request.getAlias(), request.getTypeAlias(), AliasStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Alias", request.getAlias()));

        alias.setNom(request.getClient().getNom());
        alias.setPrenom(request.getClient().getPrenom());
        alias.setNumeroCompte(request.getNumeroCompte());
        alias.setTypeCompte(request.getTypeCompte());
        alias.setStatut(AliasStatus.MODIFIED);
        alias.setDateModificationRac(response != null && response.get("dateModification") != null
                ? parseDateTime(String.valueOf(response.get("dateModification"))) : null);
        aliasRepository.save(alias);

        return AliasResponse.builder()
                .endToEndId(endToEndId)
                .statut(StatutOperationAlias.SUCCES)
                .alias(request.getAlias())
                .typeAlias(request.getTypeAlias())
                .dateModification(formatDateTime(alias.getDateModificationRac()))
                .build();
    }

    @Transactional
    public AliasResponse deleteAlias(TypeAlias typeAlias, String aliasValue, String raisonSuppression) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> payload = new HashMap<>();
        payload.put("endToEndId", endToEndId);
        payload.put("alias", aliasValue);
        payload.put("raisonSuppression", raisonSuppression);

        messageLogService.log(null, endToEndId, IsoMessageType.RAC_DELETE, MessageDirection.OUTBOUND, payload, null, null);
        aipClient.post("/alias/suppression", payload);

        PiAlias alias = aliasRepository.findByAliasValueAndTypeAliasAndStatut(aliasValue, typeAlias, AliasStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Alias", aliasValue));

        alias.setStatut(AliasStatus.DELETED);
        aliasRepository.save(alias);

        return AliasResponse.builder()
                .endToEndId(endToEndId)
                .statut(StatutOperationAlias.SUCCES)
                .alias(aliasValue)
                .build();
    }

    public Map<String, Object> searchAlias(TypeAlias typeAlias, String alias) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> payload = new HashMap<>();
        payload.put("endToEndId", endToEndId);
        payload.put("alias", alias);
        payload.put("typeAlias", typeAlias.name());

        messageLogService.log(null, endToEndId, IsoMessageType.RAC_SEARCH, MessageDirection.OUTBOUND, payload, null, null);
        return aipClient.post("/alias/recherche", payload);
    }

    public Page<AliasResponse> listAliases(Pageable pageable) {
        return aliasRepository.findByCodeMembreParticipantAndStatut(
                properties.getCodeMembre(), AliasStatus.ACTIVE, pageable)
                .map(a -> AliasResponse.builder()
                        .endToEndId(a.getEndToEndId())
                        .alias(a.getAliasValue())
                        .dateCreation(formatDateTime(a.getDateCreationRac()))
                        .build());
    }

    private Map<String, Object> buildAliasPayload(String endToEndId, AliasCreationRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idCreationAlias", IdGenerator.generateEndToEndId(properties.getCodeMembre()));
        payload.put("endToEndId", endToEndId);
        payload.put("alias", request.getAlias());
        payload.put("typeAlias", request.getTypeAlias().name());
        payload.put("typeClient", request.getClient().getTypeClient().name());
        payload.put("nom", request.getClient().getNom());
        payload.put("typeIdentifiant", request.getClient().getTypeIdentifiant().name());
        payload.put("identifiant", request.getClient().getIdentifiant());
        payload.put("telephone", request.getClient().getTelephone());
        payload.put("numeroCompte", request.getNumeroCompte());
        payload.put("typeCompte", request.getTypeCompte().name());
        payload.put("dateOuvertureCompte", request.getDateOuvertureCompte());
        payload.put("codeMembreParticipant", properties.getCodeMembre());
        if (request.getClient().getPrenom() != null) payload.put("prenom", request.getClient().getPrenom());
        if (request.getClient().getAutrePrenom() != null) payload.put("autrePrenom", request.getClient().getAutrePrenom());
        if (request.getClient().getRaisonSociale() != null) payload.put("raisonSociale", request.getClient().getRaisonSociale());
        if (request.getClient().getDateNaissance() != null) payload.put("dateNaissance", request.getClient().getDateNaissance());
        if (request.getClient().getLieuNaissance() != null) payload.put("lieuNaissance", request.getClient().getLieuNaissance());
        if (request.getClient().getNationalite() != null) payload.put("nationalite", request.getClient().getNationalite());
        if (request.getClient().getAdresse() != null) payload.put("adresse", request.getClient().getAdresse());
        if (request.getClient().getVille() != null) payload.put("ville", request.getClient().getVille());
        if (request.getClient().getPays() != null) payload.put("pays", request.getClient().getPays());
        if (request.getClient().getEmail() != null) payload.put("email", request.getClient().getEmail());
        if (request.getCodeAgence() != null) payload.put("codeAgence", request.getCodeAgence());
        if (request.getNomAgence() != null) payload.put("nomAgence", request.getNomAgence());
        if (request.getPhotoClient() != null) payload.put("photoClient", request.getPhotoClient());
        if (request.getPreConfirmation() != null) payload.put("preConfirmation", request.getPreConfirmation());
        if (request.getMarchand() != null) {
            if (request.getMarchand().getCodeMarchand() != null) payload.put("codeMarchand", request.getMarchand().getCodeMarchand());
            if (request.getMarchand().getCategorieCodeMarchand() != null) payload.put("categorieCodeMarchand", request.getMarchand().getCategorieCodeMarchand());
            if (request.getMarchand().getNomMarchand() != null) payload.put("nomMarchand", request.getMarchand().getNomMarchand());
            if (request.getMarchand().getVilleMarchand() != null) payload.put("villeMarchand", request.getMarchand().getVilleMarchand());
            if (request.getMarchand().getPaysMarchand() != null) payload.put("paysMarchand", request.getMarchand().getPaysMarchand());
        }
        return payload;
    }

}
