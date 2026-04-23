-- Restores an identifiant_transaction column on pi_transfer — BCEAO AIP
-- rejects PACS.008 with "TransactionIdentification est obligatoire" for
-- canals 400, 733, 500, 521, 520, 631, 401 when this field isn't carried
-- in the payload. Spec §4.3.1.1 (p.68 of BCEAO-Doc.pdf):
--
--   identifiantTransaction → <CdtTrfTxInf>/<PmtId>/<TxId>, 1-35 chars.
--
-- The V26 cleanup dropped the old `reference_client` column that used to
-- carry this value in the legacy TransferService; we re-introduce it under
-- its canonical BCEAO name so the field purpose is self-evident.

ALTER TABLE pi_transfer
    ADD COLUMN identifiant_transaction VARCHAR(35);
