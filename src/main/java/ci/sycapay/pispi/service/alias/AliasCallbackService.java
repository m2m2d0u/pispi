package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.enums.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Response example: {dateCreation=2026-04-16T20:11:38.886Z, idCreationAlias=ECIE00220260416201136HIKYZWUGL1K8IH,
     *                   alias=5411001, type=MCOD, statut=SUCCES}
     */
    @Transactional
    public void processCreationResponse(Map<String, Object> payload) {
        String idCreationAlias = (String) payload.get("idCreationAlias");
        String statut = (String) payload.get("statut");
        String aliasValue = (String) payload.get("alias");
        String typeStr = (String) payload.get("type");
        String dateCreation = payload.get("dateCreation") != null
                ? String.valueOf(payload.get("dateCreation")) : null;

        if (idCreationAlias == null) {
            log.warn("Alias creation callback missing idCreationAlias: {}", payload);
            return;
        }

        Optional<PiAlias> aliasOpt = aliasRepository.findByEndToEndId(idCreationAlias);
        if (aliasOpt.isEmpty()) {
            log.warn("Alias not found for idCreationAlias: {}", idCreationAlias);
            return;
        }

        PiAlias alias = aliasOpt.get();

        // Update with actual values from PI-RAC
        if (aliasValue != null) {
            alias.setAliasValue(aliasValue);
        }
        if (typeStr != null) {
            try {
                alias.setTypeAlias(TypeAlias.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown alias type: {}", typeStr);
            }
        }
        if (dateCreation != null) {
            alias.setDateCreationRac(parseDateTime(dateCreation));
        }

        // Set status based on response
        if ("SUCCES".equalsIgnoreCase(statut)) {
            alias.setStatut(AliasStatus.ACTIVE);
            log.info("Alias {} activated successfully", aliasValue);
        } else {
            alias.setStatut(AliasStatus.FAILED);
            log.warn("Alias creation failed for {}: {}", idCreationAlias, payload);
        }

        aliasRepository.save(alias);

        // Notify via webhook
        webhookService.notify(
                "SUCCES".equalsIgnoreCase(statut) ? WebhookEventType.ALIAS_CREATED : WebhookEventType.ALIAS_FAILED,
                alias.getEndToEndId(),
                null,
                payload
        );
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
            if (dateSuppression != null) {
                aliasEntity.setDateSuppressionRac(parseDateTime(dateSuppression));
            }
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
