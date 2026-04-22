package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

/**
 * Processes alias callbacks from PI-RAC. Per BCEAO §4.1 we only update
 * operational fields on our own alias rows ({@code statut}, dates, the
 * generated SHID value) — we never populate client PII locally, regardless of
 * what the callback carries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliasCallbackService {

    private final PiAliasRepository aliasRepository;
    private final WebhookService webhookService;

    /**
     * Creation callback. A single request may correspond to an MBNO + SHID pair
     * sharing the same {@code idCreationAlias}; on SUCCES both are activated
     * and the SHID row takes its PI-generated value from the {@code shid} field.
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
                if (valueForRow != null) alias.setAliasValue(valueForRow);
                if (parsedDate != null) alias.setDateCreationRac(parsedDate);
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
                idCreationAlias, null, payload);
    }

    private String valueFor(TypeAlias type, String aliasField, String shidField) {
        if (type == TypeAlias.SHID) {
            return shidField != null ? shidField : aliasField;
        }
        return aliasField;
    }

    /**
     * Modification callback. BCEAO cascades the modification server-side to
     * the client's whole alias family — locally we mirror only the
     * {@code dateModificationRac} across the family (identified by
     * {@code backOfficeClientId}) so the {@code dateLastSync} / display paths
     * stay coherent.
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

        Optional<PiAlias> aliasOpt = aliasRepository.findByAliasValue(alias);
        if (aliasOpt.isEmpty()) {
            log.warn("Alias not found for modification: {}", alias);
            return;
        }

        PiAlias aliasEntity = aliasOpt.get();
        boolean success = "SUCCES".equalsIgnoreCase(statut);
        LocalDateTime parsedDate = dateModification != null ? parseDateTime(dateModification) : null;

        List<PiAlias> affected = aliasEntity.getBackOfficeClientId() != null
                ? aliasRepository.findAllByBackOfficeClientIdAndStatut(
                        aliasEntity.getBackOfficeClientId(), AliasStatus.ACTIVE)
                : List.of(aliasEntity);
        if (affected.isEmpty()) affected = List.of(aliasEntity);

        if (parsedDate != null) {
            for (PiAlias row : affected) row.setDateModificationRac(parsedDate);
            aliasRepository.saveAll(affected);
        }

        if (success) {
            log.info("Alias {} modified successfully ({} row(s) updated)", alias, affected.size());
        } else {
            log.warn("Alias modification failed for {}: {}", alias, payload);
        }

        webhookService.notify(
                success ? WebhookEventType.ALIAS_MODIFIED : WebhookEventType.ALIAS_FAILED,
                aliasEntity.getEndToEndId(), null, payload);
    }

    /** Deletion callback. Flips the row to {@code DELETED} and records the date. */
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

        Optional<PiAlias> aliasOpt = aliasRepository.findByAliasValue(alias);
        if (aliasOpt.isEmpty()) {
            log.warn("Alias not found for deletion: {}", alias);
            return;
        }

        PiAlias aliasEntity = aliasOpt.get();
        boolean success = "SUCCES".equalsIgnoreCase(statut);

        if (success) {
            aliasEntity.setStatut(AliasStatus.DELETED);
            aliasEntity.setDateSuppressionRac(dateSuppression != null
                    ? parseDateTime(dateSuppression) : LocalDateTime.now());
            log.info("Alias {} deleted successfully", alias);
        } else {
            log.warn("Alias deletion failed for {}: {}", alias, payload);
        }

        aliasRepository.save(aliasEntity);

        webhookService.notify(
                success ? WebhookEventType.ALIAS_DELETED : WebhookEventType.ALIAS_FAILED,
                aliasEntity.getEndToEndId(), null, payload);
    }

    /**
     * Search callback. Per BCEAO §4.1 we DO NOT populate {@code pi_alias}
     * from an inbound search result — replicating the alias base is
     * explicitly forbidden. The search result lives only in
     * {@code pi_message_log} (written by the controller) so
     * {@link ci.sycapay.pispi.service.resolver.ClientSearchResolver} can build
     * a transfer/RTP payload directly from it.
     *
     * <p>This handler's sole local side-effect is: if the searched alias
     * happens to be one of our own and we already have a row for it, we
     * enrich the outgoing webhook with that row's sibling aliases (MBNO+SHID
     * companion) so the back office can surface them without extra calls.
     */
    public Map<String, Object> enrichSearchCallbackPayload(Map<String, Object> payload) {
        String aliasValue = (String) payload.get("valeurAlias");
        if (aliasValue == null) return payload;

        return aliasRepository.findByAliasValue(aliasValue)
                .filter(a -> a.getBackOfficeClientId() != null && a.getId() != null)
                .map(primary -> {
                    List<Map<String, Object>> siblings = aliasRepository
                            .findAllByBackOfficeClientIdAndStatut(
                                    primary.getBackOfficeClientId(), AliasStatus.ACTIVE)
                            .stream()
                            .filter(a -> !a.getId().equals(primary.getId()))
                            .map(this::toSiblingSummary)
                            .toList();
                    if (siblings.isEmpty()) return payload;
                    Map<String, Object> enriched = new HashMap<>(payload);
                    enriched.put("aliasesLies", siblings);
                    return enriched;
                })
                .orElse(payload);
    }

    private Map<String, Object> toSiblingSummary(PiAlias a) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("endToEndId", a.getEndToEndId());
        summary.put("typeAlias", a.getTypeAlias() != null ? a.getTypeAlias().name() : null);
        summary.put("valeurAlias", a.getAliasValue());
        summary.put("statut", a.getStatut() != null ? a.getStatut().name() : null);
        if (a.getDateCreationRac() != null) {
            summary.put("dateCreation", a.getDateCreationRac().toString());
        }
        return summary;
    }
}
