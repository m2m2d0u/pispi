package ci.sycapay.pispi.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRejectionDto {

    private String msgId;
    private String msgIdDemande;
    private String codeRaisonRejet;
    private String descriptionRejet;
}
