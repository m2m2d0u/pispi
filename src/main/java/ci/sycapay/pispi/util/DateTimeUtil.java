package ci.sycapay.pispi.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtil {

    public static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter COMPACT_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private DateTimeUtil() {}

    public static String nowIso() {
        return LocalDateTime.now().format(ISO_DATETIME);
    }

    public static String nowCompact() {
        return LocalDateTime.now().format(COMPACT_DATETIME);
    }
}
