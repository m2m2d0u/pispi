package ci.sycapay.pispi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Bean
    public RestClient aipRestClient(PiSpiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getAipBaseUrl())
                .defaultHeader("API_KEY_PI_VALEUR", properties.getAipApiKey())
                .build();
    }
}
