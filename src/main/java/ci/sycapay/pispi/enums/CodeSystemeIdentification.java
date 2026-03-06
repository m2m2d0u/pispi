package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CodeSystemeIdentification {
    TXID("Tax Identification Number"),
    CCPT("Carte Consulaire / Passport"),
    NIDN("National Identity Number");

    private final String description;
}
