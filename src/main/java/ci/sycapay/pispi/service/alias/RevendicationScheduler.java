package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.entity.PiAliasRevendication;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.StatutRevendication;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.repository.PiAliasRevendicationRepository;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Automates the BCEAO PI-RAC §3 revendication timeline as a last-resort safety
 * net when admi.004 callbacks are missed:
 * <ul>
 *   <li><b>Day 7 (délai de réponse)</b>: lock the disputed alias locally
 *       ({@link AliasStatus#LOCKED}) and flag the claim as {@code verrouille}.</li>
 *   <li><b>Day 14 (délai de clôture)</b>: if the claim is still {@code INITIEE},
 *       auto-accept on behalf of the détenteur with {@code auteurAction=PARTICIPANT}.</li>
 * </ul>
 *
 * <p>Both jobs are idempotent — they query only the rows that have not yet
 * been transitioned and skip any that were handled via a direct admi.004
 * notification in the meantime.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevendicationScheduler {

    private static final long RESPONSE_WINDOW_DAYS = 7L;
    private static final long CLOSURE_WINDOW_DAYS = 14L;

    private final PiAliasRevendicationRepository revendicationRepository;
    private final PiAliasRepository aliasRepository;
    private final RevendicationService revendicationService;
    private final WebhookService webhookService;

    /**
     * Runs hourly. Locks aliases of revendications that entered {@code INITIEE}
     * more than 7 days ago and have not yet been flagged {@code verrouille}.
     * Safe to run even when admi.004 already locked the row — the query
     * excludes already-locked rows.
     */
    @Scheduled(fixedDelayString = "${sycapay.pi-spi.revendication-scheduler.fixed-delay-ms:3600000}",
            initialDelayString = "${sycapay.pi-spi.revendication-scheduler.initial-delay-ms:60000}")
    @Transactional
    public void lockExpiredResponseWindow() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RESPONSE_WINDOW_DAYS);
        List<PiAliasRevendication> toLock = revendicationRepository
                .findByStatutAndVerrouilleFalseAndDateInitiationLessThan(
                        StatutRevendication.INITIEE, cutoff);

        for (PiAliasRevendication rev : toLock) {
            try {
                LocalDateTime now = LocalDateTime.now();
                rev.setVerrouille(true);
                rev.setDateVerrouillage(now);
                revendicationRepository.save(rev);

                aliasRepository.findByAliasValue(rev.getAliasValue()).ifPresent(a -> {
                    if (a.getStatut() != AliasStatus.LOCKED) {
                        a.setStatut(AliasStatus.LOCKED);
                        aliasRepository.save(a);
                    }
                });

                log.info("Revendication {} — alias {} locked after 7-day response window "
                                + "(scheduler fallback)",
                        rev.getIdentifiantRevendication(), rev.getAliasValue());

                webhookService.notify(WebhookEventType.ALIAS_LOCKED, null,
                        rev.getIdentifiantRevendication(),
                        Map.of("alias", rev.getAliasValue(),
                                "dateVerrouillage", now.toString(),
                                "source", "scheduler"));
            } catch (Exception e) {
                log.error("Failed to lock revendication {}: {}",
                        rev.getIdentifiantRevendication(), e.getMessage(), e);
            }
        }
    }

    /**
     * Runs hourly. Accepts on the participant's behalf any revendication that
     * has been {@code INITIEE} for more than 14 days — the detenteur's client
     * did not reject during the 7-day closure window.
     */
    @Scheduled(fixedDelayString = "${sycapay.pi-spi.revendication-scheduler.fixed-delay-ms:3600000}",
            initialDelayString = "${sycapay.pi-spi.revendication-scheduler.initial-delay-ms:60000}")
    public void autoAcceptExpiredClosureWindow() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(CLOSURE_WINDOW_DAYS);
        List<PiAliasRevendication> toAccept = revendicationRepository
                .findByStatutAndDateInitiationLessThan(StatutRevendication.INITIEE, cutoff);

        for (PiAliasRevendication rev : toAccept) {
            // Only auto-accept inbound claims (we are the detenteur) — we
            // cannot accept on behalf of another participant for our outbound
            // claims. Without an explicit direction filter above, this guard
            // is the backstop.
            if (rev.getDirection() != ci.sycapay.pispi.enums.MessageDirection.INBOUND) {
                continue;
            }
            try {
                revendicationService.acceptClaim(rev.getIdentifiantRevendication(), "PARTICIPANT");
                log.info("Revendication {} auto-accepted after 14-day closure window "
                                + "(auteurAction=PARTICIPANT)",
                        rev.getIdentifiantRevendication());
            } catch (Exception e) {
                log.error("Failed to auto-accept revendication {}: {}",
                        rev.getIdentifiantRevendication(), e.getMessage(), e);
            }
        }
    }
}
