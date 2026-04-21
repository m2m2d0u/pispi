package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.CodeSystemeIdentification;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.enums.TypeClient;
import ci.sycapay.pispi.enums.TypeCompte;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.enums.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

/**
 * Service for processing alias callback responses from PI-RAC.
 * Updates the local alias entity with the actual values returned by PI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliasCallbackService {

    private final PiAliasRepository aliasRepository;
    private final WebhookService webhookService;

    /**
     * Process alias creation callback response.
     *
     * A single PI callback may resolve more than one local row: an MBNO creation
     * is paired with a SHID row that shares the same idCreationAlias, and PI
     * returns the generated SHID value on the {@code shid} field of the same
     * response. We load every row for the endToEndId and update them together:
     *  - On SUCCES: MBNO keeps its phone-number value; SHID takes the {@code shid}
     *    value from the payload. Both become ACTIVE with the same dateCreation.
     *  - On failure: all rows sharing the endToEndId are marked FAILED.
     */
    @Transactional
    public void processCreationResponse(Map<String, Object> payload) {
        String idCreationAlias = (String) payload.get("idCreationAlias");
        String statut = (String) payload.get("statut");
        String dateCreation = payload.get("dateCreation") != null
                ? String.valueOf(payload.get("dateCreation")) : null;

        if (idCreationAlias == null) {
            log.warn("Alias creation callback missing idCreationAlias: {}", payload);
            return;
        }

        List<PiAlias> aliases = aliasRepository.findAllByEndToEndId(idCreationAlias);
        if (aliases.isEmpty()) {
            log.warn("No alias found for idCreationAlias: {}", idCreationAlias);
            return;
        }

        String aliasValue = (String) payload.get("alias");
        String shidValue = (String) payload.get("shid");
        LocalDateTime parsedDate = dateCreation != null ? parseDateTime(dateCreation) : null;
        boolean success = "SUCCES".equalsIgnoreCase(statut);

        for (PiAlias alias : aliases) {
            if (success) {
                String valueForRow = valueFor(alias.getTypeAlias(), aliasValue, shidValue);
                if (valueForRow != null) {
                    alias.setAliasValue(valueForRow);
                }
                if (parsedDate != null) {
                    alias.setDateCreationRac(parsedDate);
                }
                alias.setStatut(AliasStatus.ACTIVE);
                log.info("Alias {} ({}) activated (e2e={})",
                        alias.getAliasValue(), alias.getTypeAlias(), idCreationAlias);
            } else {
                alias.setStatut(AliasStatus.FAILED);
                log.warn("Alias {} ({}) creation failed (e2e={}): {}",
                        alias.getAliasValue(), alias.getTypeAlias(), idCreationAlias, payload);
            }
        }

        aliasRepository.saveAll(aliases);

        webhookService.notify(
                success ? WebhookEventType.ALIAS_CREATED : WebhookEventType.ALIAS_FAILED,
                idCreationAlias,
                null,
                payload
        );
    }

    /**
     * SHID rows take the PI-generated value from {@code shid}. MBNO/MCOD rows keep
     * what was submitted unless the payload includes an {@code alias} field to echo.
     * Returning {@code null} leaves the row's current value untouched.
     */
    private String valueFor(TypeAlias type, String aliasField, String shidField) {
        if (type == TypeAlias.SHID) {
            return shidField != null ? shidField : aliasField;
        }
        return aliasField;
    }

    /**
     * Process alias modification callback response.
     */
    @Transactional
    public void processModificationResponse(Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        String statut = (String) payload.get("statut");
        String dateModification = payload.get("dateModification") != null
                ? String.valueOf(payload.get("dateModification")) : null;

        if (alias == null) {
            log.warn("Alias modification callback missing alias: {}", payload);
            return;
        }

        // Find by alias value and ACTIVE status
        Optional<PiAlias> aliasOpt = aliasRepository.findByAliasValueAndTypeAliasAndStatut(
                alias, null, AliasStatus.ACTIVE);

        // If not found with null type, try to find by alias value only
        if (aliasOpt.isEmpty()) {
            aliasOpt = findByAliasValue(alias);
        }

        if (aliasOpt.isEmpty()) {
            log.warn("Alias not found for modification: {}", alias);
            return;
        }

        PiAlias aliasEntity = aliasOpt.get();

        if (dateModification != null) {
            aliasEntity.setDateModificationRac(parseDateTime(dateModification));
        }

        if (!"SUCCES".equalsIgnoreCase(statut)) {
            log.warn("Alias modification failed for {}: {}", alias, payload);
        } else {
            log.info("Alias {} modified successfully", alias);
        }

        aliasRepository.save(aliasEntity);

        webhookService.notify(
                "SUCCES".equalsIgnoreCase(statut) ? WebhookEventType.ALIAS_MODIFIED : WebhookEventType.ALIAS_FAILED,
                aliasEntity.getEndToEndId(),
                null,
                payload
        );
    }

    /**
     * Process alias deletion callback response.
     */
    @Transactional
    public void processDeletionResponse(Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        String statut = (String) payload.get("statut");
        String dateSuppression = payload.get("dateSuppression") != null
                ? String.valueOf(payload.get("dateSuppression")) : null;

        if (alias == null) {
            log.warn("Alias deletion callback missing alias: {}", payload);
            return;
        }

        Optional<PiAlias> aliasOpt = findByAliasValue(alias);
        if (aliasOpt.isEmpty()) {
            log.warn("Alias not found for deletion: {}", alias);
            return;
        }

        PiAlias aliasEntity = aliasOpt.get();

        if ("SUCCES".equalsIgnoreCase(statut)) {
            aliasEntity.setStatut(AliasStatus.DELETED);
            // Set deletion date from response or use current time as fallback
            aliasEntity.setDateSuppressionRac(dateSuppression != null
                    ? parseDateTime(dateSuppression)
                    : LocalDateTime.now());
            log.info("Alias {} deleted successfully", alias);
        } else {
            log.warn("Alias deletion failed for {}: {}", alias, payload);
        }

        aliasRepository.save(aliasEntity);

        webhookService.notify(
                "SUCCES".equalsIgnoreCase(statut) ? WebhookEventType.ALIAS_DELETED : WebhookEventType.ALIAS_FAILED,
                aliasEntity.getEndToEndId(),
                null,
                payload
        );
    }

    /**
     * Process alias search callback response.
     * Updates existing alias or creates new one with data from PI-RAC.
     *
     * Response example: {nationalite=CI, other=+2250707070710, ville=Abidjan, categorie=P,
     *                   dateNaissance=1990-01-15, typeCompte=TRAN, telephone=+2250707070710,
     *                   endToEndId=ECIE002..., nom=Diallo, participant=CIE002,
     *                   identificationNationale=CMI123456789, valeurAlias=59041c28-...,
     *                   typeAlias=SHID, statut=SUCCES, paysResidence=CI, email=...}
     */
    @Transactional
    public void processSearchResponse(Map<String, Object> payload) {
        String statut = (String) payload.get("statut");

        if (!"SUCCES".equalsIgnoreCase(statut)) {
            log.info("Alias search returned no result or failed: {}", payload);
            return;
        }

        String endToEndId = (String) payload.get("endToEndId");
        String aliasValue = (String) payload.get("valeurAlias");
        String typeAliasStr = (String) payload.get("typeAlias");
        String identifiant = (String) payload.get("identificationFiscale");

        if (endToEndId == null || aliasValue == null) {
            log.warn("Alias search response missing required fields: {}", payload);
            return;
        }

        // Try to find existing alias by endToEndId or aliasValue
        Optional<PiAlias> existingOpt = aliasRepository.findByIdentifiantAndTypeAlias(identifiant, TypeAlias.valueOf(typeAliasStr));
        if (existingOpt.isEmpty()) {
            existingOpt = findByAliasValue(aliasValue);
        }

        PiAlias alias;
        boolean isNew = false;

        if (existingOpt.isPresent()) {
            alias = existingOpt.get();
            log.info("Updating existing alias {} with search response data", aliasValue);
        } else {
            alias = new PiAlias();
            alias.setEndToEndId(endToEndId);
            isNew = true;
            log.info("Creating new alias {} from search response", aliasValue);
        }

        // Map response fields to entity
        alias.setAliasValue(aliasValue);
        alias.setStatut(AliasStatus.ACTIVE);

        if (typeAliasStr != null) {
            try {
                alias.setTypeAlias(TypeAlias.valueOf(typeAliasStr));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown alias type: {}", typeAliasStr);
            }
        }

        // Client info
        String nom = (String) payload.get("nom");
        if (nom != null) alias.setNom(nom);

        String categorie = (String) payload.get("categorie");
        if (categorie != null) {
            try {
                alias.setTypeClient(TypeClient.valueOf(categorie));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown client type: {}", categorie);
            }
        }

        String telephone = (String) payload.get("telephone");
        if (telephone != null) alias.setTelephone(telephone);

        String email = (String) payload.get("email");
        if (email != null) alias.setEmail(email);

        String nationalite = (String) payload.get("nationalite");
        if (nationalite != null) alias.setNationalite(nationalite);

        String paysResidence = (String) payload.get("paysResidence");
        if (paysResidence != null) alias.setPays(paysResidence);

        // Identification
        String identificationNationale = (String) payload.get("identificationNationale");
        if (identificationNationale != null) {
            alias.setIdentifiant(identificationNationale);
            alias.setTypeIdentifiant(CodeSystemeIdentification.NIDN);
        }
        String numeroPasseport = (String) payload.get("numeroPasseport");
        if (numeroPasseport != null) {
            alias.setIdentifiant(numeroPasseport);
            alias.setTypeIdentifiant(CodeSystemeIdentification.CCPT);
        }
        String identificationFiscale = (String) payload.get("identificationFiscale");
        if (identificationFiscale != null) {
            alias.setIdentifiant(identificationFiscale);
            alias.setTypeIdentifiant(CodeSystemeIdentification.TXID);
        }

        // Date of birth
        String dateNaissanceStr = (String) payload.get("dateNaissance");
        if (dateNaissanceStr != null) {
            try {
                alias.setDateNaissance(LocalDate.parse(dateNaissanceStr));
            } catch (Exception e) {
                log.warn("Failed to parse dateNaissance: {}", dateNaissanceStr);
            }
        }

        // Account info
        String other = (String) payload.get("other");
        if (other != null) alias.setNumeroCompte(other);

        String typeCompteStr = (String) payload.get("typeCompte");
        if (typeCompteStr != null) {
            try {
                alias.setTypeCompte(TypeCompte.valueOf(typeCompteStr));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown account type: {}", typeCompteStr);
            }
        }

        String participant = (String) payload.get("participant");
        if (participant != null) alias.setCodeMembreParticipant(participant);

        // Business info (required for type B/G)
        String raisonSociale = (String) payload.get("raisonSociale");
        if (raisonSociale != null) alias.setRaisonSociale(raisonSociale);

        // If raisonSociale not provided but denominationSociale is, use it as fallback
        String denominationSociale = (String) payload.get("denominationSociale");
        if (alias.getRaisonSociale() == null && denominationSociale != null) {
            alias.setRaisonSociale(denominationSociale);
        }

        aliasRepository.save(alias);

        log.info("Alias {} {} from search response", aliasValue, isNew ? "created" : "updated");

        webhookService.notify(
                WebhookEventType.ALIAS_SEARCH_RESULT,
                alias.getEndToEndId(),
                null,
                payload
        );
    }

    /**
     * Find alias by value (regardless of type and status).
     */
    private Optional<PiAlias> findByAliasValue(String aliasValue) {
        // Try ACTIVE first, then PENDING
        for (AliasStatus status : new AliasStatus[]{AliasStatus.ACTIVE, AliasStatus.PENDING}) {
            for (TypeAlias type : TypeAlias.values()) {
                Optional<PiAlias> opt = aliasRepository.findByAliasValueAndTypeAliasAndStatut(aliasValue, type, status);
                if (opt.isPresent()) {
                    return opt;
                }
            }
        }
        return Optional.empty();
    }
}
