package ci.sycapay.pispi.dto.alias;

import ci.sycapay.pispi.enums.StatutOperationAlias;
import ci.sycapay.pispi.enums.TypeAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AliasResponse {

    private String endToEndId;
    private StatutOperationAlias statut;
    private String alias;
    private TypeAlias typeAlias;
    private String dateCreation;
    private String dateModification;
    private String dateSuppression;
}
