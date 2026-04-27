-- taux_remise_paiement_immediat was dropped by V31 alongside montant_remise.
-- A remise can be expressed as either a fixed amount (montant_remise_paiement_immediat)
-- or a percentage rate (taux_remise_paiement_immediat / remiseRate in PAIN.013).
-- Both are needed to compute PACS.008 montant = PAIN.013 montant − discount.
ALTER TABLE pi_request_to_pay
    ADD COLUMN IF NOT EXISTS taux_remise_paiement_immediat NUMERIC(10, 4);
