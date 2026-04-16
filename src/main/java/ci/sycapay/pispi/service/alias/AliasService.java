package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.alias.AliasCreationRequest;
import ci.sycapay.pispi.dto.alias.AliasResponse;
import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.exception.AipCommunicationException;
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
import org.springframework.web.client.HttpStatusCodeException;

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

        Map<String, Object> response;
        try {
            response = aipClient.post("/alias/creation", payload);
        } catch (AipCommunicationException e) {
            Throwable cause = e.getCause();
            Integer httpStatus = null;
            String errorBody = e.getMessage();
            if (cause instanceof HttpStatusCodeException httpEx) {
                httpStatus = httpEx.getStatusCode().value();
                errorBody = httpEx.getResponseBodyAsString();
            }
            messageLogService.logError(endToEndId, IsoMessageType.RAC_CREATE,
                    Map.of("aipError", errorBody != null ? errorBody : "unknown"), httpStatus, errorBody);
            throw e;
        }

        PiAlias alias = PiAlias.builder()
                .endToEndId(endToEndId)
                .aliasValue(request.getAlias())
                .typeAlias(request.getTypeAlias())
                .typeClient(request.getClient().getTypeClient())
                .nom(request.getClient().getNom())
                .prenom(request.getClient().getPrenom())
                .raisonSociale(request.getClient().getRaisonSociale())
                .typeIdentifiant(request.getClient().getTypeIdentifiant())
                .identifiant(request.getClient().getIdentifiant())
                .nationalite(request.getClient().getNationalite())
                .pays(request.getClient().getPays())
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

        Map<String, Object> payload = buildModificationPayload(request);
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
    public AliasResponse deleteAlias(TypeAlias typeAlias, String aliasValue, CodeRaisonSuppression raisonSuppression) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        Map<String, Object> payload = new HashMap<>();
        payload.put("endToEndId", endToEndId);
        payload.put("alias", aliasValue);
        payload.put("raisonSuppression", raisonSuppression.name());

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

    // ---- AIP payload builders ----

    /**
     * Builds the creation payload matching the AIP's Alias schema.
     * Field names are dictated by the AIP OpenAPI spec (v3/api-docs).
     */
    private Map<String, Object> buildAliasPayload(String endToEndId, AliasCreationRequest request) {
        ClientInfo c = request.getClient();
        Map<String, Object> payload = new HashMap<>();

        // Required fields
        payload.put("idCreationAlias", IdGenerator.generateEndToEndId(properties.getCodeMembre()));
        payload.put("endToEndId", endToEndId);
        payload.put("typeAlias", request.getTypeAlias().name());
        payload.put("nomClient", c.getNom());
        payload.put("categorieClient", c.getTypeClient().name());
        payload.put("telephoneClient", c.getTelephone());
        payload.put("paysResidenceClient", c.getPays());
        payload.put("participant", properties.getCodeMembre());
        // CIE002 is an E-type (electronic money) participant — use 'other', not 'iban' (banks only)
        payload.put("other", request.getNumeroCompte());
        payload.put("typeCompte", request.getTypeCompte().name());
        payload.put("dateOuvertureCompte", request.getDateOuvertureCompte());

        // Alias value (optional for SHID — AIP generates it; required for MBNO/MCOD)
        if (request.getAlias() != null) payload.put("valeurAlias", request.getAlias());

        // Identifier: mapped by type to the correct AIP field
        if (c.getTypeIdentifiant() != null && c.getIdentifiant() != null) {
            switch (c.getTypeIdentifiant()) {
                case NIDN -> payload.put("identificationNationaleClient", c.getIdentifiant());
                case CCPT -> payload.put("numeroPasseport", c.getIdentifiant());
                case TXID -> payload.put("identificationFiscale", c.getIdentifiant());
            }
        }

        // Optional client fields
        if (c.getGenre() != null) payload.put("genreClient", c.getGenre());
        if (c.getRaisonSociale() != null) payload.put("raisonSociale", c.getRaisonSociale());
        if (c.getDenominationSociale() != null) payload.put("denominationSociale", c.getDenominationSociale());
        if (c.getDateNaissance() != null) payload.put("dateNaissanceClient", c.getDateNaissance());
        if (c.getLieuNaissance() != null) payload.put("villeNaissanceClient", c.getLieuNaissance());
        if (c.getPaysNaissance() != null) payload.put("paysNaissanceClient", c.getPaysNaissance());
        if (c.getNationalite() != null) payload.put("nationaliteClient", c.getNationalite());
        if (c.getAdresse() != null) payload.put("adresseClient", c.getAdresse());
        if (c.getVille() != null) payload.put("villeClient", c.getVille());
        if (c.getEmail() != null) payload.put("emailClient", c.getEmail());
        if (c.getCodePostal() != null) payload.put("codePostaleClient", c.getCodePostal());
        if (request.getPhotoClient() != null) payload.put("photoClient", request.getPhotoClient());
        // preConfirmation is only valid for type B (personne morale) clients per BCEAO data model
        if (c.getTypeClient() == TypeClient.B && request.getPreConfirmation() != null)
            payload.put("preConfirmation", request.getPreConfirmation());

        // Merchant: nomMarchand overrides denominationSociale; codeActivite maps to MCC category
        if (request.getMarchand() != null) {
            if (request.getMarchand().getNomMarchand() != null) payload.put("denominationSociale", request.getMarchand().getNomMarchand());
            if (request.getMarchand().getCategorieCodeMarchand() != null) payload.put("codeActivite", request.getMarchand().getCategorieCodeMarchand());
        }

        return payload;
    }

    /**
     * Builds the modification payload matching the AIP's ModificationAlias schema.
     * Only the fields accepted by that schema are sent (not the full creation payload).
     */
    private Map<String, Object> buildModificationPayload(AliasCreationRequest request) {
        ClientInfo c = request.getClient();
        Map<String, Object> payload = new HashMap<>();

        payload.put("alias", request.getAlias());

        if (c.getPays() != null) payload.put("paysResidenceClient", c.getPays());
        if (c.getTelephone() != null) payload.put("telephoneClient", c.getTelephone());
        if (c.getEmail() != null) payload.put("emailClient", c.getEmail());
        if (c.getAdresse() != null) payload.put("adresseClient", c.getAdresse());
        if (c.getVille() != null) payload.put("villeClient", c.getVille());
        if (c.getCodePostal() != null) payload.put("codePostalClient", c.getCodePostal());
        if (c.getRaisonSociale() != null) payload.put("denominationSociale", c.getRaisonSociale());

        // Passport update only (the only ID type accepted for modification)
        if (c.getTypeIdentifiant() == CodeSystemeIdentification.CCPT && c.getIdentifiant() != null) {
            payload.put("numeroPasseport", c.getIdentifiant());
        }

        if (request.getPhotoClient() != null) payload.put("photoClient", request.getPhotoClient());
        if (request.getPreConfirmation() != null) payload.put("preConfirmation", request.getPreConfirmation());

        return payload;
    }

}
