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

        Map<String, Object> response = aipClient.post("/api/rac/v{version}/alias", payload);

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

        Map<String, Object> response = aipClient.put("/api/rac/v{version}/alias", payload);

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
    public AliasResponse deleteAlias(TypeAlias typeAlias, String aliasValue) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> payload = new HashMap<>();
        payload.put("endToEndId", endToEndId);
        payload.put("alias", aliasValue);
        payload.put("typeAlias", typeAlias.name());

        messageLogService.log(null, endToEndId, IsoMessageType.RAC_DELETE, MessageDirection.OUTBOUND, payload, null, null);
        aipClient.delete("/api/rac/v{version}/alias", payload);

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
        return aipClient.post("/api/rac/v{version}/alias/search", payload);
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
        payload.put("endToEndId", endToEndId);
        payload.put("alias", request.getAlias());
        payload.put("typeAlias", request.getTypeAlias().name());
        payload.put("typeClient", request.getClient().getTypeClient().name());
        payload.put("nom", request.getClient().getNom());
        payload.put("prenom", request.getClient().getPrenom());
        payload.put("typeIdentifiant", request.getClient().getTypeIdentifiant().name());
        payload.put("identifiant", request.getClient().getIdentifiant());
        payload.put("telephone", request.getClient().getTelephone());
        payload.put("numeroCompte", request.getNumeroCompte());
        payload.put("typeCompte", request.getTypeCompte().name());
        payload.put("codeMembreParticipant", properties.getCodeMembre());
        return payload;
    }

}
