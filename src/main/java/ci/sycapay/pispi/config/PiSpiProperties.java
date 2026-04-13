package ci.sycapay.pispi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sycapay.pi-spi")
public class PiSpiProperties {

    private String codeMembre;
    private String webhookUrl;
    private String aipBaseUrl;
    private String aipApiKey;
    private String apiVersion = "1";

    private Mtls mtls = new Mtls();
    private Kafka kafka = new Kafka();

    @Data
    public static class Mtls {
        private String keystorePath;
        private String keystorePassword;
        private String truststorePath;
        private String truststorePassword;
        private String keystoreType = "PKCS12";
        private boolean trustAll = false;
    }

    @Data
    public static class Kafka {
        private String callbackTopic = "pi-spi-callback";
        private String webhookTopic = "pi-spi-webhook";
    }
}
