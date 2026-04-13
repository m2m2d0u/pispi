package ci.sycapay.pispi.client;

import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.exception.AipCommunicationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AipClient {

    private final RestClient aipRestClient;
    private final PiSpiProperties properties;

    @CircuitBreaker(name = "aip", fallbackMethod = "fallback")
    @Retry(name = "aip")
    public Map<String, Object> post(String path, Object body) {
        String version = properties.getApiVersion();
        String fullPath = path.replace("{version}", version);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = aipRestClient.post()
                .uri(fullPath)
                .body(body)
                .retrieve()
                .body(Map.class);
        return response;
    }

    @CircuitBreaker(name = "aip", fallbackMethod = "fallbackGet")
    @Retry(name = "aip")
    public Map<String, Object> get(String path) {
        String version = properties.getApiVersion();
        String fullPath = path.replace("{version}", version);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = aipRestClient.get()
                .uri(fullPath)
                .retrieve()
                .body(Map.class);
        return response;
    }

    @CircuitBreaker(name = "aip", fallbackMethod = "fallback")
    @Retry(name = "aip")
    public Map<String, Object> put(String path, Object body) {
        String version = properties.getApiVersion();
        String fullPath = path.replace("{version}", version);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = aipRestClient.put()
                .uri(fullPath)
                .body(body)
                .retrieve()
                .body(Map.class);
        return response;
    }

    @CircuitBreaker(name = "aip", fallbackMethod = "fallbackDelete")
    @Retry(name = "aip")
    public Map<String, Object> delete(String path, Object body) {
        String version = properties.getApiVersion();
        String fullPath = path.replace("{version}", version);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = aipRestClient.delete()
                .uri(fullPath)
                .retrieve()
                .body(Map.class);
        return response;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> fallback(String path, Object body, Throwable t) {
        log.error("AIP call failed for path {}: {}", path, t.getMessage());
        throw new AipCommunicationException("AIP service unavailable: " + t.getMessage(), t);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> fallbackGet(String path, Throwable t) {
        log.error("AIP GET call failed for path {}: {}", path, t.getMessage());
        throw new AipCommunicationException("AIP service unavailable: " + t.getMessage(), t);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> fallbackDelete(String path, Throwable t) {
        log.error("AIP DELETE call failed for path {}: {}", path, t.getMessage());
        throw new AipCommunicationException("AIP service unavailable: " + t.getMessage(), t);
    }
}
