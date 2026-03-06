package ci.sycapay.pispi.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic callbackTopic(PiSpiProperties properties) {
        return new NewTopic(properties.getKafka().getCallbackTopic(), 3, (short) 1);
    }

    @Bean
    public NewTopic webhookTopic(PiSpiProperties properties) {
        return new NewTopic(properties.getKafka().getWebhookTopic(), 3, (short) 1);
    }
}
