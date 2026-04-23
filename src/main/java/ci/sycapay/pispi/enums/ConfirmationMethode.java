package ci.sycapay.pispi.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Method used by the client to confirm a transaction from the mobile app
 * (biometric or PIN). Carried in {@code TransactionConfirmCommand.confirmationMethode}.
 */
public enum ConfirmationMethode {
    BIOMETRY("biometry"),
    PIN("pin");

    private final String code;

    ConfirmationMethode(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ConfirmationMethode fromCode(String code) {
        for (ConfirmationMethode m : values()) {
            if (m.code.equals(code)) return m;
        }
        throw new IllegalArgumentException("Unknown ConfirmationMethode: " + code);
    }
}
