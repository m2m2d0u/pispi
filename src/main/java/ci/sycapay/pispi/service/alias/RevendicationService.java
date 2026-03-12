package ci.sycapay.pispi.service.alias;

import ci.sycapay.pispi.client.AipClient;
import ci.sycapay.pispi.config.PiSpiProperties;
import ci.sycapay.pispi.dto.alias.RevendicationResponse;
import ci.sycapay.pispi.entity.PiAliasRevendication;
import ci.sycapay.pispi.enums.MessageDirection;
import ci.sycapay.pispi.enums.StatutRevendication;
import ci.sycapay.pispi.exception.ResourceNotFoundException;
import ci.sycapay.pispi.repository.PiAliasRevendicationRepository;
import ci.sycapay.pispi.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static ci.sycapay.pispi.util.DateTimeUtil.formatDateTime;
import static ci.sycapay.pispi.util.DateTimeUtil.parseDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevendicationService {

    private final PiAliasRevendicationRepository repository;
    private final AipClient aipClient;
    private final PiSpiProperties properties;

    @Transactional
    public RevendicationResponse initiateClaim(String alias) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alias", alias);

        Map<String, Object> response = aipClient.post("/api/rac/v{version}/revendication", payload);

        String identifiant = response != null ? String.valueOf(response.get("identifiantRevendication")) : null;

        PiAliasRevendication claim = PiAliasRevendication.builder()
                .identifiantRevendication(identifiant)
                .aliasValue(alias)
                .direction(MessageDirection.OUTBOUND)
                .revendicateur(properties.getCodeMembre())
                .statut(StatutRevendication.INITIEE)
                .build();
        repository.save(claim);

        return toResponse(claim);
    }

    public RevendicationResponse getClaimStatus(String identifiantRevendication) {
        aipClient.get("/api/rac/v{version}/revendication/" + identifiantRevendication);
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));
        return toResponse(claim);
    }

    @Transactional
    public RevendicationResponse acceptClaim(String identifiantRevendication) {
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("dateAction", DateTimeUtil.nowIso());
        payload.put("auteurAction", "PARTICIPANT");
        aipClient.put("/api/rac/v{version}/revendication/accepter", payload);

        claim.setStatut(StatutRevendication.ACCEPTEE);
        claim.setDateAction(parseDateTime(DateTimeUtil.nowIso()));
        claim.setAuteurAction("PARTICIPANT");
        repository.save(claim);

        return toResponse(claim);
    }

    @Transactional
    public RevendicationResponse rejectClaim(String identifiantRevendication) {
        PiAliasRevendication claim = repository.findByIdentifiantRevendication(identifiantRevendication)
                .orElseThrow(() -> new ResourceNotFoundException("Revendication", identifiantRevendication));

        Map<String, Object> payload = new HashMap<>();
        payload.put("identifiantRevendication", identifiantRevendication);
        payload.put("dateAction", DateTimeUtil.nowIso());
        aipClient.put("/api/rac/v{version}/revendication/rejeter", payload);

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
