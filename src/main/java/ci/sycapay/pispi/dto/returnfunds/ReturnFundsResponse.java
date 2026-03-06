package ci.sycapay.pispi.dto.returnfunds;

import ci.sycapay.pispi.enums.CodeRaisonDemandeRetourFonds;
import ci.sycapay.pispi.enums.CodeRaisonRejetDemandeRetourFonds;
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
    private CodeRaisonDemandeRetourFonds raison;
    private CodeRaisonRejetDemandeRetourFonds raisonRejet;
    private BigDecimal montantRetourne;
    private String createdAt;
}
