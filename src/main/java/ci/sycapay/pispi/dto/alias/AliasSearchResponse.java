package ci.sycapay.pispi.dto.alias;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.dto.common.MerchantInfo;
import ci.sycapay.pispi.enums.TypeAlias;
import ci.sycapay.pispi.enums.TypeCompte;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliasSearchResponse {

    private String alias;
    private TypeAlias typeAlias;
    private ClientInfo client;
    private String numeroCompte;
    private TypeCompte typeCompte;
    private String codeMembreParticipant;
    private MerchantInfo marchand;
}
