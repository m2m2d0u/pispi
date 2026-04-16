package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.alias.AliasCreationRequest;
import ci.sycapay.pispi.dto.alias.AliasResponse;
import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.enums.*;
import ci.sycapay.pispi.exception.AipCommunicationException;
import ci.sycapay.pispi.exception.DuplicateRequestException;
import ci.sycapay.pispi.exception.InvalidStateException;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        log.info("Creating alias with endToEndId: {}", endToEndId);

        // 0. Check if alias already exists with ACTIVE or PENDING status
        if (request.getAlias() != null) {
            Optional<PiAlias> existing = aliasRepository.findByAliasValueAndTypeAliasAndStatutIn(
                    request.getAlias(),
                    request.getTypeAlias(),
                    List.of(AliasStatus.ACTIVE, AliasStatus.PENDING)
            );
            if (existing.isPresent()) {
                throw new DuplicateRequestException("Alias", request.getAlias(), existing.get().getStatut().name());
            }
        }

        // 1. Save alias with PENDING status before sending to PI-RAC
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
                .dateNaissance(request.getClient().getDateNaissance() != null
                        ? LocalDate.parse(request.getClient().getDateNaissance()) : null)
                .nationalite(request.getClient().getNationalite())
                .pays(request.getClient().getPays())
                .telephone(request.getClient().getTelephone())
                .email(request.getClient().getEmail())
                .numeroCompte(request.getNumeroCompte())
                .typeCompte(request.getTypeCompte())
                .codeMembreParticipant(codeMembre)
                .statut(AliasStatus.PENDING)
                .build();

        if (request.getMarchand() != null) {
            alias.setCodeMarchand(request.getMarchand().getCodeMarchand());
            alias.setCategorieCodeMarchand(request.getMarchand().getCategorieCodeMarchand());
            alias.setNomMarchand(request.getMarchand().getNomMarchand());
        }

        aliasRepository.save(alias);

        // 2. Build and send request to PI-RAC
        Map<String, Object> payload = buildAliasPayload(endToEndId, request);
        log.info("Sending alias creation payload: {}", payload);
        messageLogService.log(null, endToEndId, IsoMessageType.RAC_CREATE, MessageDirection.OUTBOUND, payload, null, null);

        try {
            Map<String, Object> response = aipClient.post("/alias/creation", payload);
            log.info("Alias creation response from AIP: {}", response);
        } catch (AipCommunicationException e) {
            // Mark as FAILED if request fails
            alias.setStatut(AliasStatus.FAILED);
            aliasRepository.save(alias);

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

        // 3. Return PENDING response - callback will update to ACTIVE/FAILED
        return AliasResponse.builder()
                .endToEndId(endToEndId)
                .statut(StatutOperationAlias.EN_COURS)
                .alias(request.getAlias())
                .typeAlias(request.getTypeAlias())
                .build();
    }

    @Transactional
    public AliasResponse modifyAlias(AliasCreationRequest request) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        // 1. Find alias in PI-RAC
        PiAlias alias = aliasRepository.findByAliasValueAndTypeAliasAndStatut(
                request.getAlias(), request.getTypeAlias(), AliasStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Alias", request.getAlias()));

        ClientInfo c = request.getClient();

        // 2. Validate modification constraints per PI-RAC rules
        validateModificationConstraints(alias, c);

        // 3. Update modifiable client fields locally
        if (c.getNom() != null) alias.setNom(c.getNom());
        if (c.getPrenom() != null) alias.setPrenom(c.getPrenom());
        // Only update raisonSociale for B/G clients (validated above)
        if (c.getRaisonSociale() != null && (alias.getTypeClient() == TypeClient.B || alias.getTypeClient() == TypeClient.G)) {
            alias.setRaisonSociale(c.getRaisonSociale());
        }
        if (c.getPays() != null) alias.setPays(c.getPays());
        if (c.getTelephone() != null) alias.setTelephone(c.getTelephone());
        if (c.getEmail() != null) alias.setEmail(c.getEmail());
        if (request.getNumeroCompte() != null) alias.setNumeroCompte(request.getNumeroCompte());
        if (request.getTypeCompte() != null) alias.setTypeCompte(request.getTypeCompte());
        aliasRepository.save(alias);

        // 4. Build and send request to PI-RAC
        Map<String, Object> payload = buildModificationPayload(alias, request);
        messageLogService.log(null, endToEndId, IsoMessageType.RAC_MODIFY, MessageDirection.OUTBOUND, payload, null, null);

        aipClient.post("/alias/modification", payload);

        // 5. Return EN_COURS - callback will update dateModificationRac
        return AliasResponse.builder()
                .endToEndId(endToEndId)
                .statut(StatutOperationAlias.EN_COURS)
                .alias(request.getAlias())
                .typeAlias(request.getTypeAlias())
                .build();
    }

    /**
     * Validates PI-RAC modification constraints:
     * - denominationSociale can only be modified for B and G clients
     * - Cannot change identification type (NIDN ↔ CCPT)
     */
    private void validateModificationConstraints(PiAlias alias, ClientInfo requestClient) {
        // 1. denominationSociale/raisonSociale can only be modified for B and G clients
        if (requestClient.getRaisonSociale() != null || requestClient.getDenominationSociale() != null) {
            if (alias.getTypeClient() != TypeClient.B && alias.getTypeClient() != TypeClient.G) {
                throw new InvalidStateException(
                        "denominationSociale/raisonSociale can only be modified for type B (business) and G (government) clients");
            }
        }

        // 2. Cannot change identification type from NIDN to CCPT or vice versa
        if (requestClient.getTypeIdentifiant() != null && alias.getTypeIdentifiant() != null) {
            CodeSystemeIdentification originalType = alias.getTypeIdentifiant();
            CodeSystemeIdentification requestedType = requestClient.getTypeIdentifiant();

            // NIDN ↔ CCPT change is not allowed
            if ((originalType == CodeSystemeIdentification.NIDN && requestedType == CodeSystemeIdentification.CCPT) ||
                (originalType == CodeSystemeIdentification.CCPT && requestedType == CodeSystemeIdentification.NIDN)) {
                throw new InvalidStateException(
                        "Cannot change identification type from " + originalType + " to " + requestedType +
                        ". Original identification type must be preserved.");
            }
        }
    }

    @Transactional
    public AliasResponse deleteAlias(TypeAlias typeAlias, String aliasValue, CodeRaisonSuppression raisonSuppression) {
        String codeMembre = properties.getCodeMembre();
        String endToEndId = IdGenerator.generateEndToEndId(codeMembre);

        // 1. Find alias and mark as PENDING deletion
        PiAlias alias = aliasRepository.findByAliasValueAndTypeAliasAndStatut(aliasValue, typeAlias, AliasStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Alias", aliasValue));

        // Keep alias ACTIVE until callback confirms deletion

        // 2. Build and send request to PI-RAC
        Map<String, Object> payload = new HashMap<>();
        payload.put("endToEndId", endToEndId);
        payload.put("alias", aliasValue);
        payload.put("raisonSuppression", raisonSuppression.name());

        messageLogService.log(null, endToEndId, IsoMessageType.RAC_DELETE, MessageDirection.OUTBOUND, payload, null, null);
        aipClient.post("/alias/suppression", payload);

        // 3. Return EN_COURS - callback will update status to DELETED and set dateSuppressionRac
        return AliasResponse.builder()
                .endToEndId(endToEndId)
                .statut(StatutOperationAlias.EN_COURS)
                .alias(aliasValue)
                .typeAlias(typeAlias)
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
     * Builds the creation payload matching the interface-participant's Alias DTO.
     * Uses FLAT structure with exact field names from the decompiled DTO.
     */
    private Map<String, Object> buildAliasPayload(String endToEndId, AliasCreationRequest request) {
        ClientInfo c = request.getClient();
        Map<String, Object> payload = new HashMap<>();

        // Required fields (based on interface-participant Alias DTO)
        log.info("Building payload with idCreationAlias: {}", endToEndId);
        payload.put("idCreationAlias", endToEndId);  // Use same ID as saved in entity for callback matching
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

        // Identifier: mapped by type to the correct field
        if (c.getTypeIdentifiant() != null && c.getIdentifiant() != null) {
            switch (c.getTypeIdentifiant()) {
                case NIDN -> payload.put("identificationNationaleClient", c.getIdentifiant());
                case CCPT -> payload.put("numeroPasseport", c.getIdentifiant());
                case TXID -> payload.put("identificationFiscale", c.getIdentifiant());
            }
        }

        // Genre is required for type P and C clients
        if (c.getTypeClient() == TypeClient.P || c.getTypeClient() == TypeClient.C) {
            payload.put("genreClient", c.getGenre() != null ? c.getGenre() : "1");
        } else if (c.getGenre() != null) {
            payload.put("genreClient", c.getGenre());
        }

        // Optional client fields (matching interface-participant Alias DTO field names)
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

        // preConfirmation is only valid for type B (personne morale) clients
        if (c.getTypeClient() == TypeClient.B && request.getPreConfirmation() != null) {
            payload.put("preConfirmation", request.getPreConfirmation());
        }

        // Merchant fields
        if (request.getMarchand() != null) {
            if (request.getMarchand().getNomMarchand() != null) payload.put("denominationSociale", request.getMarchand().getNomMarchand());
            if (request.getMarchand().getCategorieCodeMarchand() != null) payload.put("codeActivite", request.getMarchand().getCategorieCodeMarchand());
        }

        return payload;
    }

    /**
     * Builds the modification payload matching the AIP's ModificationAlias schema.
     * Only the fields accepted by that schema are sent (not the full creation payload).
     * Respects PI-RAC constraints:
     * - denominationSociale only for B/G clients
     * - numeroPasseport only if alias was created with CCPT
     */
    private Map<String, Object> buildModificationPayload(PiAlias alias, AliasCreationRequest request) {
        ClientInfo c = request.getClient();
        Map<String, Object> payload = new HashMap<>();

        payload.put("alias", request.getAlias());

        if (c.getPays() != null) payload.put("paysResidenceClient", c.getPays());
        if (c.getTelephone() != null) payload.put("telephoneClient", c.getTelephone());
        if (c.getEmail() != null) payload.put("emailClient", c.getEmail());
        if (c.getAdresse() != null) payload.put("adresseClient", c.getAdresse());
        if (c.getVille() != null) payload.put("villeClient", c.getVille());
        if (c.getCodePostal() != null) payload.put("codePostalClient", c.getCodePostal());

        // denominationSociale only allowed for B and G clients
        if (c.getRaisonSociale() != null && (alias.getTypeClient() == TypeClient.B || alias.getTypeClient() == TypeClient.G)) {
            payload.put("denominationSociale", c.getRaisonSociale());
        }

        // Passport update only if alias was originally created with CCPT identification
        if (alias.getTypeIdentifiant() == CodeSystemeIdentification.CCPT && c.getIdentifiant() != null) {
            payload.put("numeroPasseport", c.getIdentifiant());
        }

        if (request.getPhotoClient() != null) payload.put("photoClient", request.getPhotoClient());
        if (request.getPreConfirmation() != null) payload.put("preConfirmation", request.getPreConfirmation());

        return payload;
    }

}
