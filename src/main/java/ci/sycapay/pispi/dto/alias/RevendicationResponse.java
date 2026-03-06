package ci.sycapay.pispi.dto.alias;

import ci.sycapay.pispi.enums.StatutRevendication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevendicationResponse {

    private String identifiantRevendication;
    private StatutRevendication statut;
    private String dateAction;
    private String auteurAction;
}
