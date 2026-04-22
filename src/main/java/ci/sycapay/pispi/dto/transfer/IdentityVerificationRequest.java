package ci.sycapay.pispi.dto.transfer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for initiating an identity verification (ACMT.023 — schéma BCEAO {@code Identite}).
 *
 * <p>Per BCEAO spec, only the target participant plus one account identifier
 * (IBAN for banks, other for EME) are required. The account holder's identity
 * is returned by the target PI in the ACMT.024 response, not sent in the request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRequest {

    /** Code du participant détenteur du compte à vérifier. */
    @NotBlank
    @Size(min = 6, max = 6)
    @Pattern(regexp = "^(BJ|BF|CI|GW|ML|NE|SN|TG)[BCDEF]\\d{3}$",
            message = "codeMembreParticipant must match (BJ|BF|CI|GW|ML|NE|SN|TG)[BCDEF]\\d{3}")
    private String codeMembreParticipant;

    /** IBAN du compte à vérifier (pour les banques). Exclusif avec {@code otherClient}. */
    @Size(max = 34)
    @Pattern(regexp = "^(?:CI|SN|ML|GW|BF|NE|BJ|TG)\\d{2}(BJ|BF|CI|GW|ML|NE|SN|TG)\\d{22}$",
            message = "ibanClient must be a valid UEMOA IBAN")
    private String ibanClient;

    /** Autre identifiant de compte (téléphone pour EME). Exclusif avec {@code ibanClient}. */
    @Size(max = 70)
    private String otherClient;

    @JsonIgnore
    @AssertTrue(message = "Exactly one of ibanClient or otherClient must be provided")
    public boolean isExactlyOneAccountIdentifierProvided() {
        boolean iban = ibanClient != null && !ibanClient.isBlank();
        boolean other = otherClient != null && !otherClient.isBlank();
        return iban ^ other;
    }
}
