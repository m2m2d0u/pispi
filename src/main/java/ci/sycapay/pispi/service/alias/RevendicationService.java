package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.alias.RevendicationResponse;
import ci.sycapay.pispi.entity.PiAliasRevendication;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.StatutRevendication;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.repository.PiAliasRevendicationRepository;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.formatDateTime;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevendicationService {

    private final PiAliasRevendicationRepository repository;
    private final PiAliasRepository aliasRepository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final WebhookService webhookService;

    /**
     * Dispatches an inbound admi.004 notification to the appropriate claim /
     * alias side-effect. Prefers structured payload fields
     * ({@code evenement}, {@code aliasValue}, {@code identifiantRevendication})
     * per BCEAO v2.0.4 (emails replaced by admi.004 with structured bodies);
     * falls back to the legacy {@code "ALIAS-{value}-VERROUILLE"} description
     * substring for backward compatibility with old AIPs.
     */
    @Transactional
    public WebhookEventType processInfoWarn(String msgId, Map<String, Object> payload) {
        String evenement = getString(payload, "evenement");
        String aliasValue = getString(payload, "aliasValue");
        String identifiantRevendication = getString(payload, "identifiantRevendication");
        String description = getString(payload, "evenementDescription");

        // 1) Structured dispatch on the event code.
        if (evenement != null) {
            WebhookEventType structured = dispatchStructured(msgId, evenement,
                    aliasValue, identifiantRevendication);
            if (structured != null) return structured;
        }

        // 2) Legacy fallback: parse "ALIAS-{value}-VERROUILLE" / "-DEVERROUILLE"
        //    from the description. Remove this path once every AIP produces
        //    structured admi.004 payloads.
        if (description != null && description.startsWith("ALIAS-")) {
            int lastDash = description.lastIndexOf('-');
            if (lastDash > 6) {
                String legacyAlias = description.substring(6, lastDash);
                String action = description.substring(lastDash + 1);
                if ("VERROUILLE".equals(action)) {
                    return applyLock(legacyAlias, msgId, "legacy-description");
                }
                if ("DEVERROUILLE".equals(action)) {
                    return applyUnlock(legacyAlias, msgId, "legacy-description");
                }
            }
        }

        return WebhookEventType.PI_NOTIFICATION;
    }

    /**
     * Structured event-code dispatch. The exact BCEAO codes for admi.004
     * revendication events are not public — this recognises the codes the
     * reference implementations emit in practice. Anything we don't recognise
     * falls through to {@link WebhookEventType#PI_NOTIFICATION}.
     */
    private WebhookEventType dispatchStructured(String msgId, String evenement,
                                                String aliasValue,
                                                String identifiantRevendication) {
        String code = evenement.trim().toUpperCase();

        // Lock / unlock events
        if (code.contains("VERROU") && !code.contains("DEVERROU")) {
            return applyLock(aliasValue, msgId, "evenement=" + evenement);
        }
        if (code.contains("DEVERROU")) {
            return applyUnlock(aliasValue, msgId, "evenement=" + evenement);
        }

        // Claim lifecycle events
        if (code.contains("REVENDICATION") || code.startsWith("REV-")
                || code.startsWith("CLAIM")) {
            if (code.contains("ACCEPT")) {
                markClaim(identifiantRevendication, StatutRevendication.ACCEPTEE);
                log.info("Revendication {} notified ACCEPTEE via admi.004 [msgId={}]",
                        identifiantRevendication, msgId);
                return WebhookEventType.CLAIM_ACCEPTED;
            }
            if (code.contains("REJET") || code.contains("REJECT")) {
                markClaim(identifiantRevendication, StatutRevendication.REJETEE);
                log.info("Revendication {} notified REJETEE via admi.004 [msgId={}]",
                        identifiantRevendication, msgId);
                return WebhookEventType.CLAIM_REJECTED;
            }
            if (code.contains("INIT") || code.contains("CREATE")) {
                return WebhookEventType.CLAIM_RECEIVED;
            }
        }

        return null;
    }

    private WebhookEventType applyLock(String aliasValue, String msgId, String reason) {
        if (aliasValue == null) {
            log.warn("Lock event received without aliasValue [msgId={}, reason={}]", msgId, reason);
            return WebhookEventType.PI_NOTIFICATION;
        }
        aliasRepository.findByAliasValue(aliasValue).ifPresentOrElse(
                alias -> {
                    alias.setStatut(AliasStatus.LOCKED);
                    aliasRepository.save(alias);
                    log.info("Alias {} locked [msgId={}, reason={}]", aliasValue, msgId, reason);
                },
                () -> log.warn("Lock received for unknown alias: {} [msgId={}]", aliasValue, msgId)
        );
        repository.findByAliasValue(aliasValue).ifPresent(rev -> {
            rev.setVerrouille(true);
            if (rev.getDateVerrouillage() == null) {
                rev.setDateVerrouillage(java.time.LocalDateTime.now());
            }
            repository.save(rev);
        });
        return WebhookEventType.ALIAS_LOCKED;
    }

    private WebhookEventType applyUnlock(String aliasValue, String msgId, String reason) {
        if (aliasValue == null) {
            log.warn("Unlock event received without aliasValue [msgId={}, reason={}]", msgId, reason);
            return WebhookEventType.PI_NOTIFICATION;
        }
        aliasRepository.findByAliasValue(aliasValue).ifPresentOrElse(
                alias -> {
                    alias.setStatut(AliasStatus.ACTIVE);
                    aliasRepository.save(alias);
                    log.info("Alias {} unlocked [msgId={}, reason={}]", aliasValue, msgId, reason);
                },
                () -> log.warn("Unlock received for unknown alias: {} [msgId={}]", aliasValue, msgId)
        );
        repository.findByAliasValue(aliasValue).ifPresent(rev -> {
            rev.setVerrouille(false);
            repository.save(rev);
        });
        return WebhookEventType.ALIAS_UNLOCKED;
    }

    private void markClaim(String identifiantRevendication, StatutRevendication statut) {
        if (identifiantRevendication == null) return;
        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(rev -> {
            rev.setStatut(statut);
            rev.setDateAction(java.time.LocalDateTime.now());
            repository.save(rev);
        });
    }

    private static String getString(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    /**
     * Initiates a revendication for an alias held by another participant (§3.1).
     * Persists the OUTBOUND record locally with statut=INITIEE <b>before</b>
     * calling the AIP so a subsequent callback can reconcile against a known
     * row. The {@code identifiantRevendication} is null at this point — it's
     * assigned by the AIP and populated on the callback.
     */
    @Transactional
    public RevendicationResponse initiateClaim(String alias) {
        String codeMembre = properties.getCodeMembre();

        // Persist immediately with a local placeholder identifier; the AIP
        // response will carry the definitive identifiantRevendication.
        String placeholder = "PENDING-" + codeMembre + "-" + System.currentTimeMillis();
        PiAliasRevendication pending = PiAliasRevendication.builder()
                .identifiantRevendication(placeholder)
                .aliasValue(alias)
                .direction(MessageDirection.OUTBOUND)
                .revendicateur(codeMembre)
                .statut(StatutRevendication.INITIEE)
                .build();
        repository.save(pending);

        Map<String, Object> payload = new HashMap<>();
        payload.put("alias", alias);
        aipClient.post("/revendications/creation", payload);

        return toResponse(pending);
    }

    @Transactional
    public void processClaimResponse(Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String detenteur = (String) payload.get("detenteur");
        String revendicateur = (String) payload.get("revendicateur");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null
                ? StatutRevendication.valueOf(statutStr)
                : StatutRevendication.INITIEE;

        String ourCode = properties.getCodeMembre();

        // Determine direction: if we are listed as the revendicateur, this is
        // the callback for an OUTBOUND claim we just initiated; otherwise it's
        // an INBOUND claim against one of our aliases.
        MessageDirection direction = ourCode.equals(revendicateur)
                ? MessageDirection.OUTBOUND
                : MessageDirection.INBOUND;

        // First try to match by identifiantRevendication (canonical). Failing that,
        // for OUTBOUND direction, reconcile the placeholder record created by
        // initiateClaim using the alias value.
        PiAliasRevendication claim = repository
                .findByIdentifiantRevendication(identifiantRevendication)
                .orElseGet(() -> {
                    if (direction == MessageDirection.OUTBOUND && alias != null) {
                        return repository.findByAliasValue(alias)
                                .filter(r -> r.getIdentifiantRevendication() != null
                                        && r.getIdentifiantRevendication().startsWith("PENDING-"))
                                .orElse(null);
                    }
                    return null;
                });

        if (claim == null) {
            claim = PiAliasRevendication.builder()
                    .aliasValue(alias)
                    .identifiantRevendication(identifiantRevendication)
                    .direction(direction)
                    .revendicateur(revendicateur)
                    .detenteur(detenteur)
                    .statut(statut)
                    .build();
        } else {
            claim.setIdentifiantRevendication(identifiantRevendication);
            claim.setDirection(direction);
            if (revendicateur != null) claim.setRevendicateur(revendicateur);
            claim.setDetenteur(detenteur);
            claim.setStatut(statut);
        }
        repository.save(claim);

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    @Transactional
    public void processClaimAcceptationResponse(Map<String, Object> payload) {
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : StatutRevendication.ACCEPTEE;

        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(claim -> {
            claim.setStatut(statut);
            claim.setDateAction(parseDateTime(payload.get("dateAction")));
            claim.setAuteurAction((String) payload.get("auteurAction"));
            repository.save(claim);
        });

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    @Transactional
    public void processClaimRejetResponse(Map<String, Object> payload) {
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : StatutRevendication.REJETEE;

        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(claim -> {
            claim.setStatut(statut);
            claim.setDateAction(parseDateTime(payload.get("dateAction")));
            repository.save(claim);
        });

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    @Transactional
    public void processClaimRecuperationResponse(Map<String, Object> payload) {
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String statutStr = (String) payload.get("statut");
        String detenteur = (String) payload.get("detenteur");
        String revendicateur = (String) payload.get("revendicateur");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : null;

        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(claim -> {
            if (statut != null) claim.setStatut(statut);
            if (detenteur != null) claim.setDetenteur(detenteur);
            if (revendicateur != null) claim.setRevendicateur(revendicateur);
            claim.setDateAction(parseDateTime(payload.get("dateAction")));
            claim.setAuteurAction((String) payload.get("auteurAction"));
            repository.save(claim);
        });
    }

    public RevendicationResponse getClaimStatus(String identifiantRevendication) {
        Map<String, Object> recuperationPayload = new HashMap<>();
        recuperationPayload.put("identifiantRevendication", identifiantRevendication);
        aipClient.post("/revendications/recuperation", recuperationPayload);
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));
        return toResponse(claim);
    }

    /**
     * Legacy signature — keeps {@code auteurAction = "PARTICIPANT"} for callers
     * that don't yet pass the explicit author (e.g. the day-14 scheduler).
     */
    public RevendicationResponse acceptClaim(String identifiantRevendication) {
        return acceptClaim(identifiantRevendication, "PARTICIPANT");
    }

    /**
     * BCEAO PI-RAC v3.0.0 §3.3: on acceptance, the participant must indicate
     * who decided — {@code "CLIENT"} (user explicitly accepted) or
     * {@code "PARTICIPANT"} (the participant-detenteur accepted after the
     * 14-day window expired without a client decision).
     */
    @Transactional
    public RevendicationResponse acceptClaim(String identifiantRevendication, String auteurAction) {
        if (!"CLIENT".equals(auteurAction) && !"PARTICIPANT".equals(auteurAction)) {
            throw new IllegalArgumentException(
                    "auteurAction must be 'CLIENT' or 'PARTICIPANT' per BCEAO PI-RAC v3.0.0");
        }
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        String now = DateTimeUtil.nowIso();
        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("dateAction", now);
        payload.put("auteurAction", auteurAction);
        aipClient.post("/revendications/acceptation", payload);

        claim.setStatut(StatutRevendication.ACCEPTEE);
        claim.setDateAction(parseDateTime(now));
        claim.setAuteurAction(auteurAction);
        repository.save(claim);

        return toResponse(claim);
    }

    /**
     * BCEAO PI-RAC §3.4: only the detenteur's client can reject a revendication,
     * so {@code auteurAction} is forced to {@code "CLIENT"}.
     */
    @Transactional
    public RevendicationResponse rejectClaim(String identifiantRevendication) {
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        String now = DateTimeUtil.nowIso();
        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("dateAction", now);
        payload.put("auteurAction", "CLIENT");
        aipClient.post("/revendications/rejet", payload);

        claim.setStatut(StatutRevendication.REJETEE);
        claim.setDateAction(parseDateTime(now));
        claim.setAuteurAction("CLIENT");
        repository.save(claim);

        return toResponse(claim);
    }

    private RevendicationResponse toResponse(PiAliasRevendication c) {
        return RevendicationResponse.builder()
                .identifiantRevendication(c.getIdentifiantRevendication())
                .alias(c.getAliasValue())
                .statut(c.getStatut())
                .detenteur(c.getDetenteur())
                .revendicateur(c.getRevendicateur())
                .dateAction(formatDateTime(c.getDateAction()))
                .auteurAction(c.getAuteurAction())
                .build();
    }
}
