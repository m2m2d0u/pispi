package ci.sycapay.pispi.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Slf4j
public final class DateTimeUtil {

    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter COMPACT_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DateTimeUtil() {
    }

    public static String nowIso() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
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
        try {
            String dateString = String.valueOf(dateValue);
            return LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE);
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
}
