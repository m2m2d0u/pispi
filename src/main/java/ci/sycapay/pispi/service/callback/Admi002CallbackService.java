package ci.sycapay.pispi.service.callback;

import ci.sycapay.pispi.entity.PiMessageLog;
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

        String rejectionMsgId = str(payload, "msgId");
        String originalMsgId = firstNonNull(str(payload, "msgIdDemande"),
                                            str(payload, "msgIdOriginal"));
        String endToEndId = str(payload, "endToEndId");
        String codeRaison = firstNonNull(str(payload, "codeRaisonRejet"),
                                         str(payload, "codeRaison"));
        String detail = firstNonNull(str(payload, "detailEchec"),
                                     str(payload, "descriptionRejet"));
        String fullDetail = codeRaison != null && detail != null
                ? codeRaison + " — " + detail
                : firstNonNull(detail, codeRaison);

        // 1) Dedup
        if (rejectionMsgId != null && messageLogService.isDuplicate(rejectionMsgId)) {
            log.info("ADMI.002 {} already processed, skipping", rejectionMsgId);
            return;
        }

        // 2) Log the inbound rejection
        messageLogService.log(rejectionMsgId, endToEndId, IsoMessageType.ADMI_002,
                MessageDirection.INBOUND, payload, 202, fullDetail);
        log.error("ADMI.002 rejection received: msgId={} originalMsgId={} endToEndId={} "
                        + "code={} detail={}",
                rejectionMsgId, originalMsgId, endToEndId, codeRaison, detail);

        // 3) Resolve the rejected message's type
        IsoMessageType originalType = resolveOriginalType(hint, originalMsgId, endToEndId);

        // 4) Route the update to the right domain
        boolean updated = false;
        if (originalType != null) {
            updated = switch (originalType) {
                case PACS_008, PACS_002, PACS_028 ->
                        applyToTransfer(originalMsgId, endToEndId, codeRaison, fullDetail);
                case PAIN_013, PAIN_014 ->
                        applyToRtp(endToEndId, codeRaison, fullDetail);
                case ACMT_023, ACMT_024 ->
                        applyToVerification(endToEndId, codeRaison, fullDetail);
                case CAMT_056, PACS_004, CAMT_029 ->
                        applyToReturnRequest(endToEndId, codeRaison, fullDetail);
                case RAC_CREATE, RAC_MODIFY, RAC_DELETE ->
                        applyToAlias(endToEndId, fullDetail);
                default -> {
                    log.info("ADMI.002 for {} has no dedicated handler — logged only",
                            originalType);
                    yield false;
                }
            };
        } else {
            log.warn("ADMI.002 received without resolvable original type "
                            + "(originalMsgId={}, endToEndId={}) — logged only",
                    originalMsgId, endToEndId);
        }

        // 5) Webhook — the back office decides how to surface this
        webhookService.notify(WebhookEventType.MESSAGE_REJECTED,
                endToEndId, rejectionMsgId, payload);

        if (updated) {
            log.info("ADMI.002 rejection applied to {} (endToEndId={})",
                    originalType, endToEndId);
        }
    }

    // =======================================================================
    // Domain updaters
    // =======================================================================

    private boolean applyToTransfer(String originalMsgId, String endToEndId,
                                    String codeRaison, String detail) {
        // Prefer lookup by the rejected msgId (unique per outbound transfer);
        // fall back to endToEndId when only that is available.
        Optional<ci.sycapay.pispi.entity.PiTransfer> hit = Optional.empty();
        if (originalMsgId != null) hit = transferRepository.findByMsgId(originalMsgId);
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
                    originalMsgId, endToEndId);
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
     * Resolve the original message type, preferring the caller-supplied hint
     * (the controller endpoint knows the context — {@code /transferts/echecs}
     * means PACS.008, etc.). When absent, looks up the referenced
     * {@code msgIdDemande} in {@code pi_message_log}.
     */
    private IsoMessageType resolveOriginalType(IsoMessageType hint,
                                               String originalMsgId,
                                               String endToEndId) {
        if (hint != null) return hint;
        if (originalMsgId != null) {
            Optional<PiMessageLog> original = messageLogRepository.findByMsgId(originalMsgId);
            if (original.isPresent()) return original.get().getMessageType();
        }
        // Last resort — scan on endToEndId with a permissive query. Not
        // implemented yet because every BCEAO rejection we've seen in practice
        // carries one of msgIdDemande/msgIdOriginal.
        return null;
    }

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
