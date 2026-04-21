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
                .alias(alias)
                .statut(StatutRevendication.INITIEE)
                .build();
    }

    @Transactional
    public void processClaimResponse(Map<String, Object> payload) {
        String alias = (String) payload.get("alias");
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String detenteur = (String) payload.get("detenteur");
        String revendicateur = (String) payload.get("revendicateur");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : StatutRevendication.INITIEE;

        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElse(PiAliasRevendication.builder()
                        .aliasValue(alias)
                        .identifiantRevendication(identifiantRevendication)
                        .direction(MessageDirection.OUTBOUND)
                        .revendicateur(revendicateur != null ? revendicateur : properties.getCodeMembre())
                        .build());

        claim.setDetenteur(detenteur);
        claim.setStatut(statut);
        repository.save(claim);

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    @Transactional
    public void processClaimAcceptationResponse(Map<String, Object> payload) {
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : StatutRevendication.ACCEPTEE;

        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(claim -> {
            claim.setStatut(statut);
            claim.setDateAction(parseDateTime(payload.get("dateAction")));
            claim.setAuteurAction((String) payload.get("auteurAction"));
            repository.save(claim);
        });

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    @Transactional
    public void processClaimRejetResponse(Map<String, Object> payload) {
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String statutStr = (String) payload.get("statut");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : StatutRevendication.REJETEE;

        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(claim -> {
            claim.setStatut(statut);
            claim.setDateAction(parseDateTime(payload.get("dateAction")));
            repository.save(claim);
        });

        webhookService.notify(WebhookEventType.CLAIM_RECEIVED, null, identifiantRevendication, payload);
    }

    @Transactional
    public void processClaimRecuperationResponse(Map<String, Object> payload) {
        String identifiantRevendication = (String) payload.get("identifiantRevendication");
        String statutStr = (String) payload.get("statut");
        String detenteur = (String) payload.get("detenteur");
        String revendicateur = (String) payload.get("revendicateur");
        StatutRevendication statut = statutStr != null ? StatutRevendication.valueOf(statutStr) : null;

        repository.findByIdentifiantRevendication(identifiantRevendication).ifPresent(claim -> {
            if (statut != null) claim.setStatut(statut);
            if (detenteur != null) claim.setDetenteur(detenteur);
            if (revendicateur != null) claim.setRevendicateur(revendicateur);
            claim.setDateAction(parseDateTime(payload.get("dateAction")));
            claim.setAuteurAction((String) payload.get("auteurAction"));
            repository.save(claim);
        });
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
//        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
//                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("actionDate", DateTimeUtil.nowIso());
        payload.put("actionAuteur", "PARTICIPANT");
        aipClient.post("/revendications/acceptation", payload);


//        claim.setStatut(StatutRevendication.ACCEPTEE);
//        claim.setDateAction(parseDateTime(DateTimeUtil.nowIso()));
//        claim.setAuteurAction("PARTICIPANT");
//        repository.save(claim);

//        return toResponse(claim);
        return null;
    }

    @Transactional
    public RevendicationResponse rejectClaim(String identifiantRevendication) {
//        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
//                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("actionDate", DateTimeUtil.nowIso());
        aipClient.post("/revendications/rejet", payload);

//        claim.setStatut(StatutRevendication.REJETEE);
//        claim.setDateAction(parseDateTime(DateTimeUtil.nowIso()));
//        repository.save(claim);

//        return toResponse(claim);
        return null;
    }

    private RevendicationResponse toResponse(PiAliasRevendication c) {
        return RevendicationResponse.builder()
                .identifiantRevendication(c.getIdentifiantRevendication())
                .alias(c.getAliasValue())
                .statut(c.getStatut())
                .detenteur(c.getDetenteur())
                .revendicateur(c.getRevendicateur())
                .dateAction(formatDateTime(c.getDateAction()))
                .auteurAction(c.getAuteurAction())
                .build();
    }
}
