package ci.sycapay.pispi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sycapay.pi-spi")
public class PiSpiProperties {

    /**
     * The registered participant code for this PI-SPI. Used as the sender
     * identity in every outbound ISO 20022 message — both {@code msgId} and
     * {@code endToEndId}.
     *
     * <p>Pattern: {@code (BJ|BF|CI|GW|ML|NE|SN|TG)[BCDEF]\\d{3}} — the third
     * character encodes the participant type (B/C/D/E/F). The BCEAO AIP
     * cross-checks the outbound {@code endToEndId} against this code
     * ("le EndToEndId doit débuter par 'E<code>'") and empirically accepts
     * every participant type at that position, including E (EME), despite
     * the BCEAO OpenAPI spec advertising a narrower {@code [BCDF]} pattern.
     * Any sponsor-code rewrite would therefore trip the caller-identity check.
     */
    private String codeMembre;

    private String webhookUrl;
    private String aipBaseUrl;
    private String aipApiKey;
    private String apiVersion = "1";

    private Mtls mtls = new Mtls();

    @Data
    public static class Mtls {
        private String keystorePath;
        private String keystorePassword;
        private String truststorePath;
        private String truststorePassword;
        private String keystoreType = "PKCS12";
        private boolean trustAll = false;
    }
}
