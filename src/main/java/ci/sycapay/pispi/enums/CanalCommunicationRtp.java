package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CanalCommunicationRtp {
    FACTURE("401"),
    MARCHAND_SUR_SITE("500"),
    E_COMMERCE_LIVRAISON("520"),
    E_COMMERCE_IMMEDIAT("521"),
    PARTICULIER("631");

    private final String code;

    public static CanalCommunicationRtp fromCode(String code) {
        for (CanalCommunicationRtp c : values()) {
            if (c.code.equals(code)) return c;
        }
        throw new IllegalArgumentException("Unknown CanalCommunicationRtp code: " + code);
    }
}
