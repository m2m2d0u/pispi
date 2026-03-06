package ci.sycapay.pispi.dto.alias;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.dto.common.MerchantInfo;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.enums.TypeCompte;
import jakarta.validation.Valid;
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
public class AliasCreationRequest {

    @NotBlank @Size(max = 50)
    private String alias;

    @NotNull
    private TypeAlias typeAlias;

    @Valid @NotNull
    private ClientInfo client;

    @NotBlank @Size(max = 34)
    private String numeroCompte;

    @NotNull
    private TypeCompte typeCompte;

    @Size(max = 20)
    private String codeAgence;

    @Size(max = 140)
    private String nomAgence;

    @Valid
    private MerchantInfo marchand;
}
