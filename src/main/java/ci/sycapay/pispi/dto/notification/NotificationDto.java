package ci.sycapay.pispi.dto.notification;

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
    private String direction;
    private String evenement;
    private String evenementDescription;
    private String evenementDate;
    private String messageType;
    private String createdAt;
}
