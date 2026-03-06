package ci.sycapay.pispi.dto.transfer;

import ci.sycapay.pispi.enums.EtatParticipant;
import ci.sycapay.pispi.enums.TypeParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDto {

    private String codeMembreParticipant;
    private String nomParticipant;
    private EtatParticipant etatParticipant;
    private TypeParticipant typeParticipant;
    private String paysParticipant;
    private String participantSponsor;
}
