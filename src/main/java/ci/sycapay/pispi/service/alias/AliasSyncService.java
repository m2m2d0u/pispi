package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.entity.PiAlias;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implements BCEAO PI-RAC §4.4 option 2: compare the participant's
 * back-office "client updated at" timestamp with {@code pi_alias.dateLastSync}
 * and emit a modification call to PI-RAC when the local data is older.
 *
 * <p>Usage: the participant's back office calls
 * {@code POST /api/v1/aliases/{aliasValue}/sync-check?backOfficeUpdatedAt=...}
 * whenever it persists a client change. This service re-reads the alias row
 * and decides whether a RAC modification is necessary.
 *
 * <p>The actual modification call is delegated to {@link AliasService} so the
 * full validation + payload builder path is reused. For now this service
 * only detects staleness and bumps the sync timestamp — wiring the
 * delta-to-modification build is an optional extension.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliasSyncService {

    private final PiAliasRepository aliasRepository;

    public enum SyncOutcome { UP_TO_DATE, STALE_MARK_FOR_RESYNC, UNKNOWN_ALIAS }

    /**
     * Mark the given alias as needing a refresh with PI-RAC if the caller's
     * back-office update time is more recent than our local {@code dateLastSync}.
     * The caller is expected to follow up with {@code PUT /api/v1/aliases} when
     * the outcome is {@code STALE_MARK_FOR_RESYNC}.
     */
    @Transactional
    public SyncOutcome checkAndFlag(String aliasValue, LocalDateTime backOfficeUpdatedAt) {
        PiAlias alias = aliasRepository.findByAliasValue(aliasValue)
                .orElseThrow(() -> new ResourceNotFoundException("Alias", aliasValue));

        if (alias.getStatut() == AliasStatus.DELETED) {
            return SyncOutcome.UNKNOWN_ALIAS;
        }
        if (backOfficeUpdatedAt == null) {
            // No back-office reference — treat as no-op.
            return SyncOutcome.UP_TO_DATE;
        }
        LocalDateTime lastSync = alias.getDateLastSync() != null
                ? alias.getDateLastSync()
                : alias.getDateCreationRac() != null
                        ? alias.getDateCreationRac()
                        : alias.getCreatedAt();
        if (lastSync != null && !backOfficeUpdatedAt.isAfter(lastSync)) {
            return SyncOutcome.UP_TO_DATE;
        }

        log.info("Alias {} flagged for resync — back-office updated at {} > lastSync {}",
                aliasValue, backOfficeUpdatedAt, lastSync);
        return SyncOutcome.STALE_MARK_FOR_RESYNC;
    }

    /**
     * Bump the last-sync timestamp after a successful modification call. Called
     * by {@link AliasService} when {@code modifyAlias} completes.
     */
    @Transactional
    public void recordSync(String aliasValue) {
        aliasRepository.findByAliasValue(aliasValue).ifPresent(alias -> {
            alias.setDateLastSync(LocalDateTime.now());
            aliasRepository.save(alias);
        });
    }
}
