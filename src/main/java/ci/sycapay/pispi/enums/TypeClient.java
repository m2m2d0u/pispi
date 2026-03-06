package ci.sycapay.pispi.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeClient {
    P("Personne physique"),
    B("Business"),
    G("Government"),
    C("Commercial");

    private final String description;
}
