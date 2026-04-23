package ci.sycapay.pispi.service.resolver;

import ci.sycapay.pispi.dto.common.ClientInfo;
import ci.sycapay.pispi.enums.TypeCompte;

/**
 * Outcome of resolving a participant (payeur or payé) from an inbound
 * RAC_SEARCH log entry in {@code pi_message_log}. Carries the enriched
 * {@link ClientInfo} plus the account-level data (other/iban, typeCompte),
 * the alias value, the participant's own code membre, and optional
 * type-C commercial identifiers required by the pacs.008 / pain.013 spec.
 *
 * <p>Account number: exactly one of {@code other} or {@code iban} is non-null.
 * Non-IBAN (EME/TRAL) accounts carry {@code other}; bank (CACC/SVGS) accounts
 * carry {@code iban}.
 *
 * <p>Type-C commercial IDs ({@code identificationFiscaleCommercant},
 * {@code identificationRccm}) are populated only for type-C clients and are
 * null for all other client types.
 */
public record ResolvedClient(
        ClientInfo clientInfo,
        String other,
        String iban,
        TypeCompte typeCompte,
        String aliasValue,
        String codeMembre,
        String identificationFiscaleCommercant,
        String identificationRccm
) {}
