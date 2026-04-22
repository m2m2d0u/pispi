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
     * identity in every outbound {@code msgId} (BCEAO pattern allows types
     * {@code [BCDEF]}, so EMEs can be message senders).
     *
     * <p>When {@code codeMembre} is an EME (type {@code E}), outbound messages
     * whose BCEAO schema restricts the {@code endToEndId} participant type to
     * {@code [BCDF]} (ACMT.023 {@code Identite}, PAIN.013
     * {@code DemandePaiement}) cannot carry this code in the endToEndId — a
     * {@link #codeMembreSponsor sponsoring direct-participant code} must be
     * configured instead.
     */
    private String codeMembre;

    /**
     * Optional code membre of the sponsoring direct participant (bank, caisse —
     * types {@code B|C|D|F}). Used exclusively to populate the
     * {@code endToEndId} of outbound messages whose BCEAO schema requires a
     * direct-participant identity at that position (currently ACMT.023
     * {@code Identite} and PAIN.013 {@code DemandePaiement}).
     *
     * <p>The {@code msgId} always uses {@link #codeMembre} because the BCEAO
     * pattern there accepts the sender's real identity (including type
     * {@code E}).
     *
     * <p>Falls back to {@link #codeMembre} when blank. Services validate the
     * resolved code's participant type and reject initiation with a 400 when
     * it is not {@code [BCDF]}.
     */
    private String codeMembreSponsor;

    private String webhookUrl;
    private String aipBaseUrl;
    private String aipApiKey;
    private String apiVersion = "1";

    /**
     * Resolve the sponsor code used in the {@code endToEndId}:
     * {@link #codeMembreSponsor} when set, otherwise {@link #codeMembre}.
     */
    public String resolveCodeMembreSponsor() {
        return (codeMembreSponsor != null && !codeMembreSponsor.isBlank())
                ? codeMembreSponsor
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
