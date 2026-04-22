package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Safety net for alias creation callbacks that never arrive. When
 * {@code AliasService.sendCreationToAip} succeeds, the local row remains
 * {@code PENDING} until {@code AliasCallbackService} activates it on the
 * {@code /alias/creation/reponses} callback. If PI-RAC silently drops the
 * response, the row stays {@code PENDING} forever.
 *
 * <p>This scheduler flips any {@code PENDING} row older than a configurable
 * threshold (default 5 minutes) to {@code FAILED} and fires a
 * {@link WebhookEventType#ALIAS_FAILED} webhook so the back office can retry
 * or surface the issue to the user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AliasCreationTimeoutScheduler {

    private final PiAliasRepository aliasRepository;
    private final WebhookService webhookService;

    @Value("${sycapay.pi-spi.alias-timeout-scheduler.timeout-seconds:300}")
    private long timeoutSeconds;

    @Scheduled(fixedDelayString = "${sycapay.pi-spi.alias-timeout-scheduler.fixed-delay-ms:60000}",
            initialDelayString = "${sycapay.pi-spi.alias-timeout-scheduler.initial-delay-ms:30000}")
    @Transactional
    public void expirePendingAliases() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(timeoutSeconds);
        List<PiAlias> stale = aliasRepository.findByStatutAndCreatedAtLessThan(
                AliasStatus.PENDING, cutoff);

        for (PiAlias alias : stale) {
            try {
                alias.setStatut(AliasStatus.FAILED);
                aliasRepository.save(alias);
                log.warn("Alias {} (type={}, e2e={}) flipped PENDING → FAILED after "
                                + "{}s with no callback",
                        alias.getAliasValue(), alias.getTypeAlias(),
                        alias.getEndToEndId(), timeoutSeconds);
                webhookService.notify(WebhookEventType.ALIAS_FAILED, null, alias.getEndToEndId(),
                        Map.of(
                                "endToEndId", alias.getEndToEndId(),
                                "typeAlias", String.valueOf(alias.getTypeAlias()),
                                "aliasValue", String.valueOf(alias.getAliasValue()),
                                "reason", "creation_callback_timeout",
                                "timeoutSeconds", timeoutSeconds
                        ));
            } catch (Exception e) {
                log.error("Failed to expire PENDING alias {}: {}",
                        alias.getEndToEndId(), e.getMessage(), e);
            }
        }
    }
}
