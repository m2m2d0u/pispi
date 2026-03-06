package ci.sycapay.pispi.dto.report;

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
    private String balanceType;
    private BigDecimal montant;
    private String operationType;
    private String dateBalance;
}
