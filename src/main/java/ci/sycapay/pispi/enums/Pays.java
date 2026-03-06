package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Pays {
    BJ("Benin"),
    BF("Burkina Faso"),
    CI("Cote d'Ivoire"),
    GW("Guinea-Bissau"),
    ML("Mali"),
    NE("Niger"),
    SN("Senegal"),
    TG("Togo");

    private final String label;
}
