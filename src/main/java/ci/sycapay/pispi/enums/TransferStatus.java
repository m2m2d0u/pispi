package ci.sycapay.pispi.enums;

public enum TransferStatus {
    /** Pre-confirmation state introduced by the two-phase mobile flow
     *  ({@code POST /transferts} → {@code PUT /transferts/{id}}). Nothing
     *  has been emitted to the AIP yet; the PACS.008 / PAIN.013 send happens
     *  only on confirm. Maps to remote-spec {@code statut=initie}. */
    INITIE,
    PEND, ACCC, ACSC, ACSP, RJCT, TMOT, ECHEC
}
