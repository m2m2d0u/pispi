package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.alias.RevendicationResponse;
import ci.sycapay.pispi.entity.PiAliasRevendication;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.StatutRevendication;
import ci.sycapay.pispi.enums.WebhookEventType;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRevendicationRepository;
import ci.sycapay.pispi.service.WebhookService;
import ci.sycapay.pispi.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.formatDateTime;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Service
@RequiredArgsConstructor
public class RevendicationService {

    private final PiAliasRevendicationRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;
    private final WebhookService webhookService;

    public RevendicationResponse initiateClaim(String alias) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alias", alias);

        aipClient.post("/revendications/creation", payload);

        return RevendicationResponse.builder()
                .statut(StatutRevendication.INITIEE)
                .build();
    }

    @Transactional
    public void processClaimResponse(Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String detenteur = (String) payload.get("detenteur");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : StatutRevendication.INITIEE;

        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElse(PiAliasRevendication.builder()
                        .aliasValue(alias)
                        .identifiantRevendication(identifiantRevendication)
                        .direction(MessageDirection.OUTBOUND)
                        .revendicateur(properties.getCodeMembre())
                        .build());

        claim.setDetenteur(detenteur);
        claim.setStatut(statut);
        repository.save(claim);

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    public RevendicationResponse getClaimStatus(String identifiantRevendication) {
        Map<String, Object> recuperationPayload = new HashMap<>();
        recuperationPayload.put("identifiantRevendication", identifiantRevendication);
        aipClient.post("/revendications/recuperation", recuperationPayload);
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));
        return toResponse(claim);
    }

    public RevendicationResponse acceptClaim(String identifiantRevendication) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("actionDate", DateTimeUtil.nowIso());
        payload.put("actionAuteur", "PARTICIPANT");
        aipClient.post("/revendications/acceptation", payload);

        return RevendicationResponse.builder()
                .identifiantRevendication(identifiantRevendication)
                .statut(StatutRevendication.ACCEPTEE)
                .build();
    }

    @Transactional
    public RevendicationResponse rejectClaim(String identifiantRevendication) {
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("actionDate", DateTimeUtil.nowIso());
        aipClient.post("/revendications/rejet", payload);

        claim.setStatut(StatutRevendication.REJETEE);
        claim.setDateAction(parseDateTime(DateTimeUtil.nowIso()));
        repository.save(claim);

        return toResponse(claim);
    }

    private RevendicationResponse toResponse(PiAliasRevendication c) {
        return RevendicationResponse.builder()
                .identifiantRevendication(c.getIdentifiantRevendication())
                .statut(c.getStatut())
                .dateAction(formatDateTime(c.getDateAction()))
                .auteurAction(c.getAuteurAction())
                .build();
    }
}
