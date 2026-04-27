-- V31 dropped montant_achat, montant_retrait and frais_retrait from
-- pi_request_to_pay as part of a dead-column cleanup. They are actually
-- required to build a correct PACS.008 when accepting an inbound RTP —
-- the AIP validates that the accepting transfer's monetary breakdown
-- matches the original PAIN.013 (codeRaisonRejet=TransactionNotFound).
ALTER TABLE pi_request_to_pay
    ADD COLUMN IF NOT EXISTS montant_achat                 NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS montant_retrait               NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS frais_retrait                 NUMERIC(18, 2);
