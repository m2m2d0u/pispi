package ci.sycapay.pispi.dto.common;

import ci.sycapay.pispi.enums.WebhookEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    private WebhookEventType eventType;
    private String endToEndId;
    private String msgId;
    private LocalDateTime timestamp;
    private Object payload;
}
