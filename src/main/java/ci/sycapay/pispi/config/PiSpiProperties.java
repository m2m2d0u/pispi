package ci.sycapay.pispi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sycapay.pi-spi")
public class PiSpiProperties {

    private String codeMembre;

    /**
     * Optional code membre used to generate the {@code endToEndId} of outbound
     * ACMT.023 identity verification requests. Falls back to {@link #codeMembre}
     * when null/blank.
     *
     * <p>The BCEAO {@code Identite} schema restricts the verification
     * {@code endToEndId} to participant types {@code [BCDF]} (see
     * documentation/interface-participant-openapi.json, ~line 790). An EME
     * ({@code [E]}) PI therefore cannot directly initiate an ACMT.023 — set this
     * property to a non-EME proxy code (e.g. {@code CIB002}) to bypass the AIP
     * pattern check on the endToEndId.
     *
     * <p>The {@code msgId} always uses {@link #codeMembre} because the AIP
     * enforces that {@code BizMsgIdr} starts with {@code M<real-codeMembre>}.
     */
    private String codeMembreVerification;

    private String webhookUrl;
    private String aipBaseUrl;
    private String aipApiKey;
    private String apiVersion = "1";

    /**
     * Returns {@link #codeMembreVerification} when set; otherwise falls back to
     * {@link #codeMembre}. Used by the identity verification service to generate
     * ACMT.023 identifiers.
     */
    public String resolveCodeMembreVerification() {
        return (codeMembreVerification != null && !codeMembreVerification.isBlank())
                ? codeMembreVerification
                : codeMembre;
    }

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
