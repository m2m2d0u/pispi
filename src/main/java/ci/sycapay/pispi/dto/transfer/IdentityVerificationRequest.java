package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.TypeCompte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRequest {

    @NotBlank @Size(min = 6, max = 6)
    private String codeMembreParticipantPaye;

    @NotBlank @Size(max = 34)
    private String numeroCompteClientPaye;

    @NotNull
    private TypeCompte typeCompteClientPaye;

    @Size(max = 140)
    private String nomClientPaye;

    @Size(max = 140)
    private String prenomClientPaye;
}
