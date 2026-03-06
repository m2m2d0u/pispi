package ci.sycapay.pispi.dto.returnfunds;

import ci.sycapay.pispi.enums.ReturnRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnFundsResponse {

    private String identifiantDemande;
    private String endToEndId;
    private ReturnRequestStatus statut;
    private String raison;
    private String raisonRejet;
    private BigDecimal montantRetourne;
    private String createdAt;
}
