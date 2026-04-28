package ci.sycapay.pispi.repository;

import ci.sycapay.pispi.entity.PiIdentityVerification;
import ci.sycapay.pispi.enums.MessageDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PiIdentityVerificationRepository extends JpaRepository<PiIdentityVerification, Long> {

    /** Direction-scoped lookup — preferred from callback handlers. */
    Optional<PiIdentityVerification> findByEndToEndIdAndDirection(String endToEndId, MessageDirection direction);

    /**
     * Direction-agnostic best-effort lookup. With the composite unique on
     * {@code (end_to_end_id, direction)} (V42), an OUTBOUND and INBOUND row
     * can share the same id (self-loop / multi-tenant) — this helper returns
     * the most recent.
     */
    Optional<PiIdentityVerification> findFirstByEndToEndIdOrderByIdDesc(String endToEndId);

    /**
     * @deprecated May throw {@code NonUniqueResultException} when both legs
     *             coexist locally. Prefer {@link #findByEndToEndIdAndDirection}
     *             or {@link #findFirstByEndToEndIdOrderByIdDesc}.
     */
    @Deprecated
    Optional<PiIdentityVerification> findByEndToEndId(String endToEndId);
}
