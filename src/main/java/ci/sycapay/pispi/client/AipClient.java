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
        String fullPath = resolvePath(path);
        long t0 = System.currentTimeMillis();
        String e2e = extractEndToEndId(body);
        log.info("[AIP →] POST {} e2e={}", fullPath, e2e);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = aipRestClient.post()
                    .uri(fullPath)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            log.info("[AIP ←] POST {} e2e={} → 2xx in {}ms", fullPath, e2e, System.currentTimeMillis() - t0);
            return response;
        } catch (RuntimeException e) {
            log.warn("[AIP ←] POST {} e2e={} → FAILED in {}ms : {}",
                    fullPath, e2e, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    @CircuitBreaker(name = "aip", fallbackMethod = "fallbackGet")
    @Retry(name = "aip")
    public Map<String, Object> get(String path) {
        String fullPath = resolvePath(path);
        long t0 = System.currentTimeMillis();
        log.info("[AIP →] GET {}", fullPath);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = aipRestClient.get()
                    .uri(fullPath)
                    .retrieve()
                    .body(Map.class);
            log.info("[AIP ←] GET {} → 2xx in {}ms", fullPath, System.currentTimeMillis() - t0);
            return response;
        } catch (RuntimeException e) {
            log.warn("[AIP ←] GET {} → FAILED in {}ms : {}",
                    fullPath, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    @CircuitBreaker(name = "aip", fallbackMethod = "fallback")
    @Retry(name = "aip")
    public Map<String, Object> put(String path, Object body) {
        String fullPath = resolvePath(path);
        long t0 = System.currentTimeMillis();
        String e2e = extractEndToEndId(body);
        log.info("[AIP →] PUT {} e2e={}", fullPath, e2e);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = aipRestClient.put()
                    .uri(fullPath)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            log.info("[AIP ←] PUT {} e2e={} → 2xx in {}ms", fullPath, e2e, System.currentTimeMillis() - t0);
            return response;
        } catch (RuntimeException e) {
            log.warn("[AIP ←] PUT {} e2e={} → FAILED in {}ms : {}",
                    fullPath, e2e, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    @CircuitBreaker(name = "aip", fallbackMethod = "fallbackDelete")
    @Retry(name = "aip")
    public Map<String, Object> delete(String path, Object body) {
        String fullPath = resolvePath(path);
        long t0 = System.currentTimeMillis();
        log.info("[AIP →] DELETE {}", fullPath);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = aipRestClient.delete()
                    .uri(fullPath)
                    .retrieve()
                    .body(Map.class);
            log.info("[AIP ←] DELETE {} → 2xx in {}ms", fullPath, System.currentTimeMillis() - t0);
            return response;
        } catch (RuntimeException e) {
            log.warn("[AIP ←] DELETE {} → FAILED in {}ms : {}",
                    fullPath, System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    private String resolvePath(String path) {
        return path.replace("{version}", properties.getApiVersion());
    }

    /** Best-effort extraction of {@code endToEndId} from common BCEAO payload shapes for log correlation. */
    @SuppressWarnings("unchecked")
    private static String extractEndToEndId(Object body) {
        if (body instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get("endToEndId");
            if (v != null) return v.toString();
        }
        return null;
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
