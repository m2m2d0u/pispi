package ci.sycapay.pispi.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdGenerator() {}

    public static String generateMsgId(String codeMembre) {
        return "M" + codeMembre + randomAlphanumeric(28);
    }

    public static String generateEndToEndId(String codeMembre) {
        String date = LocalDateTime.now().format(DATE_FMT);
        return "E" + codeMembre + date + randomAlphanumeric(14);
    }

    public static String generateReturnRequestId(String codeMembre) {
        return "C" + codeMembre + randomAlphanumeric(28);
    }

    public static String generateIdentifiantReleve(String codeMembre, String dateCompensation) {
        return "RECO" + codeMembre + dateCompensation;
    }

    private static String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
