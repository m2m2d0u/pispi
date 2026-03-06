package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeTransaction {
    PRMG("Paiement Marchand/Govt"),
    DISP("Disponibilite/Transfer");

    private final String description;
}
