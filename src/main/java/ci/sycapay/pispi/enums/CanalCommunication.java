package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CanalCommunication {
    QR_CODE("731"),
    ADRESSE_PAIEMENT("633"),
    ORDRE_TRANSFERT("999"),
    QR_CODE_STATIQUE("000"),
    QR_CODE_DYNAMIQUE("400"),
    API_BUSINESS("733"),
    FACTURE("401"),
    MARCHAND_SUR_SITE("500"),
    E_COMMERCE_LIVRAISON("520"),
    E_COMMERCE_IMMEDIAT("521"),
    PARTICULIER("631"),
    USSD("300");

    private final String code;

    public static CanalCommunication fromCode(String code) {
        for (CanalCommunication c : values()) {
            if (c.code.equals(code)) return c;
        }
        throw new IllegalArgumentException("Unknown CanalCommunication code: " + code);
    }
}
