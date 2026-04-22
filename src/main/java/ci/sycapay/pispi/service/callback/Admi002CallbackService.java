package ci.sycapay.pispi.service.callback;

import ci.sycapay.pispi.entity.PiMessageLog;
import ci.sycapay.pispi.entity.PiTransfer;
import ci.sycapay.pispi.enums.AliasStatus;
import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.ReturnRequestStatus;
import ci.sycapay.pispi.enums.RtpStatus;
import ci.sycapay.pispi.enums.TransferStatus;
import ci.sycapay.pispi.enums.VerificationStatus;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.repository.PiAliasRepository;
import ci.sycapay.pispi.repository.PiIdentityVerificationRepository;
import ci.sycapay.pispi.repository.PiMessageLogRepository;
import ci.sycapay.pispi.repository.PiRequestToPayRepository;
import ci.sycapay.pispi.repository.PiReturnRequestRepository;
import ci.sycapay.pispi.repository.PiTransferRepository;
import ci.sycapay.pispi.service.MessageLogService;
import ci.sycapay.pispi.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Handles every inbound BCEAO AIP rejection encoded as an ADMI.002 message.
 *
 * <p>ADMI.002 is the structural rejection BCEAO emits when a message the PI
 * sent (pacs.008, pain.013, acmt.023, camt.056, alias ops, …) fails schema
 * validation or a business pre-check. The AIP lands it on one of several
 * error endpoints; this service is the common backend — each controller
 * endpoint just forwards the raw payload here.
 *
 * <p>Workflow per event:
 * <ol>
 *   <li>Deduplicate on the rejection's own {@code msgId}.</li>
 *   <li>Log the raw payload in {@code pi_message_log} with type
 *       {@code ADMI_002}, inbound direction, and the rejected message's
 *       endToEndId (when present).</li>
 *   <li>Resolve the original message type — either from a caller-supplied
 *       hint or by looking up the referenced {@code msgIdDemande} in
 *       {@code pi_message_log}.</li>
 *   <li>Route to the right domain update: mark the affected transfer /
 *       RTP / verification / return / alias row FAILED and carry the
 *       {@code codeRaisonRejet} + {@code descriptionRejet} onto it.</li>
 *   <li>Fire a {@link WebhookEventType#MESSAGE_REJECTED} webhook so the
 *       back office can surface the failure to the end user.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Admi002CallbackService {

    private final MessageLogService messageLogService;
    private final PiMessageLogRepository messageLogRepository;
    private final PiTransferRepository transferRepository;
    private final PiRequestToPayRepository rtpRepository;
    private final PiIdentityVerificationRepository verificationRepository;
    private final PiReturnRequestRepository returnRequestRepository;
    private final PiAliasRepository aliasRepository;
    private final WebhookService webhookService;

    /**
     * Process an inbound ADMI.002 rejection.
     *
     * @param payload raw JSON payload pushed by the AIP
     * @param hint    optional hint about the type of the rejected message
     *                (the endpoint URL usually disambiguates — e.g. the
     *                {@code /transferts/echecs} callback passes PACS_008,
     *                the {@code /verifications-identites/echecs} callback
     *                passes ACMT_023). When absent we fall back to looking up
     *                the original message in {@code pi_message_log}.
     */
    @Transactional
    public void handleRejection(Map<String, Object> payload, IsoMessageType hint) {
        if (payload == null) {
            log.warn("ADMI.002 invoked with null payload");
            return;
        }

        String msgId = str(payload, "msgId");

        String endToEndId = str(payload, "endToEndId");
        String codeRaison = firstNonNull(str(payload, "codeRaisonRejet"),
                                         str(payload, "codeRaison"));
        String detail = firstNonNull(str(payload, "detailEchec"),
                                     str(payload, "descriptionRejet"));
        String fullDetail = codeRaison != null && detail != null
                ? codeRaison + " — " + detail
                : firstNonNull(detail, codeRaison);

        // 1) Dedup
        if (msgId != null && messageLogService.isDuplicateInbound(msgId)) {
            log.info("ADMI.002 {} already processed, skipping", msgId);
            return;
        }

        // 2) Log the inbound rejection
        messageLogService.log(msgId, endToEndId, IsoMessageType.ADMI_002,
                MessageDirection.INBOUND, payload, 202, fullDetail);
        log.error("ADMI.002 rejection received: msgId={} endToEndId={} "
                        + "code={} detail={}",
                msgId, endToEndId, codeRaison, detail);

        // 3) Resolve the rejected message — type + its own endToEndId (the
        //    admi.002 payload often omits the latter, so we fall back to the
        //    OUTBOUND log row written when we first sent the message).
        ResolvedOriginal original = resolveOriginal(hint, msgId, endToEndId);
        IsoMessageType originalType = original.type();
        String effectiveE2e = original.endToEndId();
        log.info("ADMI.002 resolved original: type={} endToEndId={}", originalType, effectiveE2e);

        // 4) Route the update to the right domain
        boolean updated = false;
        if (originalType != null) {
            updated = switch (originalType) {
                case PACS_008, PACS_002, PACS_028 ->
                        applyToTransfer(msgId, effectiveE2e, codeRaison, fullDetail);
                case PAIN_013, PAIN_014 ->
                        applyToRtp(effectiveE2e, codeRaison, fullDetail);
                case ACMT_023, ACMT_024 ->
                        applyToVerification(effectiveE2e, codeRaison, fullDetail);
                case CAMT_056, PACS_004, CAMT_029 ->
                        applyToReturnRequest(effectiveE2e, codeRaison, fullDetail);
                case RAC_CREATE, RAC_MODIFY, RAC_DELETE ->
                        applyToAlias(effectiveE2e, fullDetail);
                default -> {
                    log.info("ADMI.002 for {} has no dedicated handler — logged only",
                            originalType);
                    yield false;
                }
            };
        } else {
            log.warn("ADMI.002 received without resolvable original type "
                            + "(msgId={}, endToEndId={}) — logged only",
                    msgId, effectiveE2e);
        }

        // 5) Webhook — the back office decides how to surface this
        webhookService.notify(WebhookEventType.MESSAGE_REJECTED,
                effectiveE2e, msgId, payload);

        if (updated) {
            log.info("ADMI.002 rejection applied to {} (endToEndId={})",
                    originalType, effectiveE2e);
        }
    }

    // =======================================================================
    // Domain updaters
    // =======================================================================

    private boolean applyToTransfer(String msgId, String endToEndId,
                                    String codeRaison, String detail) {
        // Prefer lookup by the rejected msgId (unique per outbound transfer);
        // fall back to endToEndId when only that is available.
        Optional<PiTransfer> hit = Optional.empty();
        if (msgId != null) hit = transferRepository.findByMsgId(msgId);
        if (hit.isEmpty() && endToEndId != null) {
            hit = transferRepository.findByEndToEndIdAndDirection(
                    endToEndId, MessageDirection.OUTBOUND);
        }
        return hit.map(t -> {
            t.setStatut(TransferStatus.ECHEC);
            t.setCodeRaison(codeRaison != null ? codeRaison : "AIP_ERR");
            t.setDetailEchec(detail);
            transferRepository.save(t);
            return true;
        }).orElseGet(() -> {
            log.warn("ADMI.002 for transfer: no local row found (msgId={}, endToEndId={})",
                    msgId, endToEndId);
            return false;
        });
    }

    private boolean applyToRtp(String endToEndId, String codeRaison, String detail) {
        if (endToEndId == null) return false;
        return rtpRepository.findByEndToEndId(endToEndId)
                .map(rtp -> {
                    rtp.setStatut(RtpStatus.RJCT);
                    rtp.setCodeRaison(codeRaison != null ? codeRaison : "AIP_ERR");
                    rtpRepository.save(rtp);
                    return true;
                }).orElseGet(() -> {
                    log.warn("ADMI.002 for RTP: no local row found (endToEndId={})", endToEndId);
                    return false;
                });
    }

    private boolean applyToVerification(String endToEndId, String codeRaison, String detail) {
        if (endToEndId == null) return false;
        return verificationRepository.findByEndToEndId(endToEndId)
                .map(v -> {
                    v.setStatut(VerificationStatus.FAILED);
                    v.setCodeRaison(codeRaison);
                    verificationRepository.save(v);
                    return true;
                }).orElseGet(() -> {
                    log.warn("ADMI.002 for verification: no local row found (endToEndId={})",
                            endToEndId);
                    return false;
                });
    }

    private boolean applyToReturnRequest(String endToEndId, String codeRaison, String detail) {
        if (endToEndId == null) return false;
        return returnRequestRepository.findByEndToEndId(endToEndId)
                .map(r -> {
                    // PiReturnRequest.raisonRejet is a strict enum
                    // (CodeRaisonRejetDemandeRetourFonds: CUST|AC04|ARDT) that
                    // wouldn't fit an arbitrary BCEAO rejection code. We flip
                    // the status to RJCR and leave the free-text detail on
                    // pi_message_log — the webhook carries the full payload
                    // so the back office can display it.
                    r.setStatut(ReturnRequestStatus.RJCR);
                    returnRequestRepository.save(r);
                    return true;
                }).orElseGet(() -> {
                    log.warn("ADMI.002 for return request: no local row found (endToEndId={})",
                            endToEndId);
                    return false;
                });
    }

    /**
     * Flips the alias row to {@code FAILED} when PI-RAC rejects a create /
     * modify / delete call. The alias PII is no longer persisted locally
     * (§4.1 refactor) so we just carry the status update + rely on the
     * webhook for the reason detail.
     */
    private boolean applyToAlias(String endToEndId, String detail) {
        if (endToEndId == null) return false;
        return aliasRepository.findAllByEndToEndId(endToEndId).stream()
                .map(a -> {
                    a.setStatut(AliasStatus.FAILED);
                    aliasRepository.save(a);
                    return true;
                }).findAny()
                .orElseGet(() -> {
                    log.warn("ADMI.002 for alias: no local row found (endToEndId={})", endToEndId);
                    return false;
                });
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Resolves the rejected message. Returns both its BCEAO {@link IsoMessageType}
     * and the endToEndId stored on the original OUTBOUND log entry so downstream
     * updaters can still find the matching domain row even when the admi.002
     * payload itself omits {@code endToEndId}.
     *
     * <p>Prefers the caller-supplied hint (the controller endpoint knows the
     * context — {@code /transferts/echecs} means PACS.008, etc.), then falls
     * back to looking up the OUTBOUND log entry in {@code pi_message_log}
     * by msgId.
     *
     * <p><b>Critical</b>: the lookup MUST filter by direction=OUTBOUND. Since
     * V23 the (msg_id, direction) pair is the unique key — the same msgId now
     * also appears on the INBOUND admi.002 row we just wrote, so
     * {@code findByMsgId} alone would return two results and throw
     * {@code NonUniqueResultException}.
     */
    private ResolvedOriginal resolveOriginal(IsoMessageType hint,
                                              String msgId,
                                              String endToEndId) {
        IsoMessageType type = hint;
        String resolvedE2e = endToEndId;

        if (msgId != null) {
            Optional<PiMessageLog> original = messageLogRepository
                    .findPiMessageLogByMsgIdAndDirectionIs(msgId, MessageDirection.OUTBOUND);
            if (original.isPresent()) {
                if (type == null) type = original.get().getMessageType();
                if (resolvedE2e == null) resolvedE2e = original.get().getEndToEndId();
            }
        }

        return new ResolvedOriginal(type, resolvedE2e);
    }

    /** Tuple for {@link #resolveOriginal}. */
    private record ResolvedOriginal(IsoMessageType type, String endToEndId) {}

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
