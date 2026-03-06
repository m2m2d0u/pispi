package ci.sycapay.pispi.dto.common;

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
public class AccountInfo {

    @NotBlank @Size(max = 34)
    private String numeroCompte;

    @NotNull
    private TypeCompte typeCompte;

    @NotBlank @Size(min = 6, max = 6)
    private String codeMembreParticipant;

    @Size(max = 20)
    private String codeAgence;

    @Size(max = 140)
    private String nomAgence;
}
