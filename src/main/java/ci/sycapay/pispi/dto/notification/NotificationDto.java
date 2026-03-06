package ci.sycapay.pispi.dto.notification;

import ci.sycapay.pispi.enums.IsoMessageType;
import ci.sycapay.pispi.enums.MessageDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String msgId;
    private String msgIdDemande;
    private MessageDirection direction;
    private String evenement;
    private String evenementDescription;
    private String evenementDate;
    private IsoMessageType messageType;
    private String createdAt;
}
