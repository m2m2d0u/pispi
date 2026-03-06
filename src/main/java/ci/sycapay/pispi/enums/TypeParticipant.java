package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeParticipant {
    B("Bank"),
    F("Fintech/EME"),
    C("Commercial"),
    D("Other"),
    E("Other");

    private final String description;
}
