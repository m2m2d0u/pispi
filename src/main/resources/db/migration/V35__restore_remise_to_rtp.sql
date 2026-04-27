-- montant_remise_paiement_immediat was dropped by V31 along with other
-- monetary fields. It is needed to compute the correct PACS.008 montant
-- when accepting an inbound RTP: the AIP validates that
--   PACS.008 montant = PAIN.013 montant − montantRemisePaiementImmediat
-- Omitting it causes AM09 (wrong amount).
ALTER TABLE pi_request_to_pay
    ADD COLUMN IF NOT EXISTS montant_remise_paiement_immediat NUMERIC(18, 2);
