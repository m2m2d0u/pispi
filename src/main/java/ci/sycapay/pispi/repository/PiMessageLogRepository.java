package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PiMessageLogRepository extends JpaRepository<PiMessageLog, Long> {

    /**
     * Direction-aware msgId lookup. Since V23 the {@code (msg_id, direction)}
     * pair is the composite unique key — the same msgId can legitimately
     * appear in both the OUTBOUND row we wrote when sending a message and
     * in the INBOUND row we wrote when the AIP echoed the same msgId on an
     * admi.002 rejection. Always qualify a msgId lookup by direction to
     * avoid {@code NonUniqueResultException}.
     */
    Optional<PiMessageLog> findPiMessageLogByMsgIdAndDirectionIs(
            String msgId, MessageDirection direction);

    Optional<PiMessageLog> findFirstByEndToEndIdAndDirectionAndMessageTypeOrderByCreatedAtDesc(
            String endToEndId, MessageDirection direction, IsoMessageType messageType);

    /**
     * Fetches the most recent inbound RAC_SEARCH whose JSON payload carries
     * the given {@code valeurAlias}. Used by the two-phase mobile flow when
     * the client sends a raw alias at {@code POST /transferts} rather than a
     * pre-resolved {@code endToEndIdSearch} — we look the alias up in the
     * existing search log (§4.2 forbids caching but does not forbid reading
     * the last inbound response to bridge a single interaction).
     *
     * <p>Portable JSON syntax: {@code payload ->> 'valeurAlias'} works on
     * PostgreSQL and Hibernate translates it for other dialects.
     */
    @Query(value =
            "SELECT * FROM pi_message_log m " +
            "WHERE m.direction = 'INBOUND' " +
            "  AND m.message_type = 'RAC_SEARCH' " +
            "  AND m.payload->>'valeurAlias' = :aliasValue " +
            "ORDER BY m.created_at DESC " +
            "LIMIT 1",
            nativeQuery = true)
    Optional<PiMessageLog> findLatestRacSearchByAliasValue(@Param("aliasValue") String aliasValue);
}
