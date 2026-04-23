package ci.sycapay.pispi.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Discriminator for the mobile-facing {@code /transferts} endpoint per
 * the BCEAO remote spec ({@code TransactionInitiationRequete.action}).
 *
 * <ul>
 *   <li>{@code send_now}      — immediate debit; emits PACS.008</li>
 *   <li>{@code send_schedule} — deferred (one-off or recurring); stored locally, emitted by scheduler</li>
 *   <li>{@code receive_now}   — request-to-pay; emits PAIN.013</li>
 * </ul>
 */
public enum TransactionAction {
    SEND_NOW("send_now"),
    SEND_SCHEDULE("send_schedule"),
    RECEIVE_NOW("receive_now");

    private final String code;

    TransactionAction(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TransactionAction fromCode(String code) {
        for (TransactionAction a : values()) {
            if (a.code.equals(code)) return a;
        }
        throw new IllegalArgumentException("Unknown TransactionAction: " + code);
    }
}
