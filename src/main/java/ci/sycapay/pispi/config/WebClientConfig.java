package ci.sycapay.pispi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient aipRestClient(PiSpiProperties properties) throws Exception {
        PiSpiProperties.Mtls mtls = properties.getMtls();

        SSLContext sslContext = buildSslContext(mtls);

        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        return RestClient.builder()
                .baseUrl(properties.getAipBaseUrl())
                .defaultHeader("API_KEY_PI_VALEUR", properties.getAipApiKey())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    private SSLContext buildSslContext(PiSpiProperties.Mtls mtls) throws Exception {
        KeyManager[] keyManagers = loadKeyManagers(mtls);
        TrustManager[] trustManagers = resolveTrustManagers(mtls);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        return sslContext;
    }

    private KeyManager[] loadKeyManagers(PiSpiProperties.Mtls mtls) throws Exception {
        String path = mtls.getKeystorePath();
        if (path == null || path.isBlank()) {
            log.warn("No mTLS keystore configured — client certificate authentication disabled");
            return null;
        }

        String password = mtls.getKeystorePassword() != null ? mtls.getKeystorePassword() : "";
        KeyStore keyStore = KeyStore.getInstance(mtls.getKeystoreType());

        try (InputStream is = resolveInputStream(path)) {
            keyStore.load(is, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        log.info("mTLS keystore loaded from: {}", path);
        return kmf.getKeyManagers();
    }

    private TrustManager[] resolveTrustManagers(PiSpiProperties.Mtls mtls) throws Exception {
        if (mtls.isTrustAll()) {
            log.warn("TRUST-ALL mode enabled — server certificate validation is disabled. DO NOT use in production.");
            return new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            }};
        }

        String path = mtls.getTruststorePath();
        if (path == null || path.isBlank()) {
            log.info("No custom truststore configured — using JVM default trust store");
            return null;
        }

        String password = mtls.getTruststorePassword() != null ? mtls.getTruststorePassword() : "";
        KeyStore trustStore = KeyStore.getInstance(mtls.getKeystoreType());

        try (InputStream is = resolveInputStream(path)) {
            trustStore.load(is, password.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        log.info("mTLS truststore loaded from: {}", path);
        return tmf.getTrustManagers();
    }

    private InputStream resolveInputStream(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
            if (is == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resource);
            }
            return is;
        }
        return new java.io.FileInputStream(path);
    }
}
