package ci.sycapay.pispi.service;

import ci.sycapay.pispi.client.AipClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageStatusService {

    private final AipClient aipClient;

    public Map<String, Object> checkMessageStatus(String msgId) {
        log.info("Checking message status for msgId: {}", msgId);
        return aipClient.get("/messages/statut/" + msgId);
    }
}
