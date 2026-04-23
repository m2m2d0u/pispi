package ci.sycapay.pispi.service.transaction;

import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.repository.PiTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Phase 3.4b — periodic runner that executes due {@code SEND_SCHEDULE} rows.
 *
 * <p>Finds all {@link PiTransfer} rows with {@code action = SEND_SCHEDULE},
 * {@code active = true}, and {@code next_execution_date ≤ today}, and
 * delegates each to {@link TransactionService#executeScheduledExecution(PiTransfer)}
 * which spawns a child {@code SEND_NOW} row, emits the PACS.008, and advances
 * (or deactivates) the parent.
 *
 * <h3>Scheduling</h3>
 * Runs on a fixed delay (default: every hour) with a small initial delay so
 * the job doesn't fight for startup resources. A daily-at-a-fixed-hour cron
 * would be marginally nicer for operational observability, but a fixedDelay
 * keeps parity with the other schedulers in this codebase
 * ({@code RevendicationScheduler}, {@code AliasCreationTimeoutScheduler})
 * and copes better with application restarts — a missed execution window
 * won't skip a day, just shift the next run by the delay.
 *
 * <h3>Idempotency</h3>
 * Once a schedule runs, {@code next_execution_date} advances (or the row
 * deactivates). That mutation + the child PACS.008 happen inside a single
 * {@code @Transactional} boundary on the service method, so a crash between
 * emit and DB commit would re-emit the PACS.008 on the next tick — the AIP
 * deduplicates by {@code msgId} (always freshly generated), and our own
 * {@link ci.sycapay.pispi.service.MessageLogService#isDuplicate} guard gates
 * the inbound callback side. A burst of duplicate emits is therefore the
 * failure mode rather than a missed execution, which is the direction we
 * want for money movement.
 *
 * <h3>Per-schedule isolation</h3>
 * The runner catches exceptions per iteration so one bad schedule row
 * (missing snapshot, AIP rejection, etc.) doesn't poison the rest of the
 * batch. Each schedule runs on its own transaction (the service method is
 * annotated {@code @Transactional}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionScheduleRunner {

    private final PiTransferRepository transferRepository;
    private final TransactionService transactionService;

//    @Scheduled(
//            fixedDelayString = "${sycapay.pi-spi.schedule-runner.fixed-delay-ms:3600000}",
//            initialDelayString = "${sycapay.pi-spi.schedule-runner.initial-delay-ms:120000}")
    public void runDueSchedules() {
        LocalDate today = LocalDate.now();
        List<PiTransfer> due = transferRepository.findDueSchedules(today);
        if (due.isEmpty()) {
            log.debug("No schedules due on {}", today);
            return;
        }

        log.info("Running {} due schedule(s) for {}", due.size(), today);
        int ok = 0, failed = 0;
        for (PiTransfer parent : due) {
            try {
                transactionService.executeScheduledExecution(parent);
                ok++;
            } catch (Exception e) {
                // Swallow — we want to continue with the other schedules. The
                // failing row still has its next_execution_date ≤ today, so
                // it will be retried on the next tick. That's the correct
                // behaviour for transient errors (AIP unreachable, DB blip);
                // for permanent errors (stale RAC snapshot, bad canal, etc.)
                // an operator will need to deactivate the row manually.
                failed++;
                log.error("Schedule execution failed [endToEndId={}, subscriptionId={}] — "
                                + "will retry on next tick: {}",
                        parent.getEndToEndId(), parent.getSubscriptionId(), e.getMessage(), e);
            }
        }
        log.info("Schedule runner finished: {} executed, {} failed (retry pending)",
                ok, failed);
    }
}
