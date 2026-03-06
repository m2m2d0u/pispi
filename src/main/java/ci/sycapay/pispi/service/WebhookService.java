package ci.sycapay.pispi.service;

import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.common.WebhookEvent;
import ci.sycapay.pispi.enums.WebhookEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final PiSpiProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Async
    public void notify(WebhookEventType eventType, String endToEndId, String msgId, Object payload) {
        String webhookUrl = properties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Webhook URL not configured, skipping notification for event {}", eventType);
            return;
        }

        WebhookEvent event = WebhookEvent.builder()
                .eventType(eventType)
                .endToEndId(endToEndId)
                .msgId(msgId)
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .build();

        try {
            restClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook sent: {} for endToEndId={}", eventType, endToEndId);
        } catch (Exception e) {
            log.error("Failed to send webhook {} for endToEndId={}: {}", eventType, endToEndId, e.getMessage());
        }
    }
}
