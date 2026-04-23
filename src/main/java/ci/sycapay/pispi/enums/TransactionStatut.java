package ci.sycapay.pispi.enums;

/**
 * Mobile-facing transaction status per BCEAO remote spec ({@code Transaction.statut}).
 * Distinct from the AIP-side {@link TransferStatus} / {@link StatutTransaction} codes;
 * this is the view returned to the mobile app.
 *
 * <ul>
 *   <li>{@code initie}      — awaiting confirmation (after POST /transferts)</li>
 *   <li>{@code irrevocable} — settled (pacs.002 ACSC / ACCC received)</li>
 *   <li>{@code rejete}      — rejected by the AIP or counterparty</li>
 *   <li>{@code desactive}   — scheduled/subscription cancelled by the user</li>
 * </ul>
 */
public enum TransactionStatut {
    INITIE,
    IRREVOCABLE,
    REJETE,
    DESACTIVE
}
