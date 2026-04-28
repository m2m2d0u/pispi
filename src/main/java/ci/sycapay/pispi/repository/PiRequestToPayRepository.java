package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiRequestToPay;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiRequestToPayRepository extends JpaRepository<PiRequestToPay, Long> {

    /**
     * Direction-scoped lookup. Use this whenever the caller knows the leg
     * (callback handlers know they want OUTBOUND for PAIN.014 responses,
     * INBOUND when accepting/rejecting an incoming RTP).
     */
    Optional<PiRequestToPay> findByEndToEndIdAndDirection(String endToEndId, MessageDirection direction);

    /**
     * Direction-agnostic lookup. With the composite unique key
     * {@code (end_to_end_id, direction)} introduced in V42, both an OUTBOUND
     * and an INBOUND row may legitimately share the same {@code endToEndId}
     * (self-loop sandbox or two participants gérés par cette plateforme
     * transactant entre eux). This helper returns the most recently inserted
     * row — for direction-sensitive operations, prefer
     * {@link #findByEndToEndIdAndDirection}.
     */
    Optional<PiRequestToPay> findFirstByEndToEndIdOrderByIdDesc(String endToEndId);

    /**
     * @deprecated The composite unique on {@code (end_to_end_id, direction)}
     *             means this method can throw {@code NonUniqueResultException}
     *             when both legs are persisted locally. Prefer
     *             {@link #findByEndToEndIdAndDirection} when the direction is
     *             known, or {@link #findFirstByEndToEndIdOrderByIdDesc} for a
     *             best-effort lookup.
     */
    @Deprecated
    Optional<PiRequestToPay> findByEndToEndId(String endToEndId);

    Page<PiRequestToPay> findByDirection(MessageDirection direction, Pageable pageable);
}
