package ci.sycapay.pispi.dto.report;

import ci.sycapay.pispi.enums.IndicateurSolde;
import ci.sycapay.pispi.enums.TypeBalanceCompense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationDto {

    private String soldeId;
    private String dateDebutCompense;
    private String dateFinCompense;
    private String participant;
    private String participantSponsor;
    private TypeBalanceCompense balanceType;
    private BigDecimal montant;
    private IndicateurSolde operationType;
    private String dateBalance;
}
