package ci.sycapay.pispi.service.resolver;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.enums.TypeCompte;

/**
 * Outcome of resolving a participant (payeur or payé) from an inbound
 * RAC_SEARCH log entry in {@code pi_message_log}. Carries the enriched
 * {@link ClientInfo} plus the account-level data (other, typeCompte),
 * the alias value and the participant's own code membre.
 */
public record ResolvedClient(
        ClientInfo clientInfo,
        String other,
        TypeCompte typeCompte,
        String aliasValue,
        String codeMembre
) {}
