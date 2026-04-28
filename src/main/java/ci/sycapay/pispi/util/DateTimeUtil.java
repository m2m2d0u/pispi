package ci.sycapay.pispi.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Slf4j
public final class DateTimeUtil {

    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter COMPACT_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DateTimeUtil() {
    }

    /**
     * Current UTC timestamp formatted to match the BCEAO/ISO-20022 strict pattern
     * {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}. We cannot use {@link Instant#toString()}
     * here because it omits trailing-zero millis (e.g. {@code 2026-04-22T18:39:29Z}
     * instead of {@code 2026-04-22T18:39:29.000Z}), which the BCEAO pain.013 /
     * pacs.008 XSDs reject.
     */
    public static String nowIso() {
        return ISO_INSTANT_MILLIS.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * Like {@link #nowIso()} but with a small forward offset. Useful for fields
     * the AIP validates with a strict {@code >= CreDtTm} check (e.g. PAIN.013
     * {@code dateHeureExecution} on canal 401) — by the time the message
     * reaches the AIP, our nowIso() value would already be slightly in the past
     * relative to the {@code CreDtTm} the AIP/transformer inserts, triggering
     * "ReqdExctnDt doit être supérieure ou égale à GrpHdr.CreDtTm". Adding a
     * few seconds keeps us safely ahead without crossing into the "débit
     * différé" territory the AIP refuses on those same canals.
     */
    public static String nowIsoPlusSeconds(long seconds) {
        return ISO_INSTANT_MILLIS.format(
                Instant.now().plusSeconds(seconds).truncatedTo(ChronoUnit.MILLIS));
    }

    public static String nowCompact() {
        return LocalDateTime.now().format(COMPACT_DATETIME);
    }

    public static LocalDateTime parseDateTime(Object dateValue) {
        if (dateValue == null) {
            return null;
        }
        try {
            String dateString = String.valueOf(dateValue);
            return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateValue, e);
            return null;
        }
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static LocalDate parseDate(Object dateValue) {
        if (dateValue == null) {
            return null;
        }
        String dateString = String.valueOf(dateValue);
        try {
            return LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateValue, e);
            return null;
        }
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ISO_DATE);
    }

    /** Strict BCEAO/ISO-20022 timestamp pattern (UTC, mandatory millisecond precision). */
    private static final DateTimeFormatter ISO_INSTANT_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Normalise an ISO-8601 timestamp to {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}.
     *
     * <p>The BCEAO pain.013 / pacs.008 XSDs reject {@code DtTm} without
     * millisecond precision (pattern
     * {@code \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z}). This helper accepts
     * inputs with or without fractional seconds and any zone offset, returns
     * UTC with {@code .SSS} always present.
     *
     * <p>Returns {@code null} for blank input and passes the original string
     * through unchanged if it cannot be parsed, so callers can at least
     * surface the problem in the outbound payload instead of silently dropping.
     */
    public static String normaliseIsoInstantMillis(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            Instant instant = Instant.parse(input).truncatedTo(ChronoUnit.MILLIS);
            return ISO_INSTANT_MILLIS.format(instant);
        } catch (Exception ignored) {
            try {
                Instant instant = OffsetDateTime.parse(input).toInstant().truncatedTo(ChronoUnit.MILLIS);
                return ISO_INSTANT_MILLIS.format(instant);
            } catch (Exception e) {
                log.warn("Failed to normalise ISO instant [{}], passing through as-is", input);
                return input;
            }
        }
    }
}
