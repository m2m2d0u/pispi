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
    private String alias;
    private StatutRevendication statut;
    private String detenteur;
    private String revendicateur;
    private String dateCreation;
    private String dateModification;
    private String dateAction;
    private String auteurAction;
    private String raisonRejet;
    private String informationsAdditionnelles;
}
