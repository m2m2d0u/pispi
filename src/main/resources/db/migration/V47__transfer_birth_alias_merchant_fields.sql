-- Gap 1: Birth data columns for payeur and payé on pi_transfer.
-- Required by BCEAO PACS.008 §4.3.1.1 pp.70-71 when the participant is a bank,
-- client is type P (personne physique), and typeCompte != TRAL.
ALTER TABLE pi_transfer
    ADD COLUMN IF NOT EXISTS date_naissance_client_payeur  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS ville_naissance_client_payeur VARCHAR(35),
    ADD COLUMN IF NOT EXISTS pays_naissance_client_payeur  VARCHAR(2),
    ADD COLUMN IF NOT EXISTS date_naissance_client_paye    VARCHAR(10),
    ADD COLUMN IF NOT EXISTS ville_naissance_client_paye   VARCHAR(35),
    ADD COLUMN IF NOT EXISTS pays_naissance_client_paye    VARCHAR(2);

-- Gap 7: Widen alias columns from 50 to 2048 (spec p.64, p.69 max=2048).
ALTER TABLE pi_transfer
    ALTER COLUMN alias_payeur TYPE VARCHAR(2048),
    ALTER COLUMN alias_paye   TYPE VARCHAR(2048);

-- Gap 3: referenceBulk — identifies transactions in a mass payment (p.69).
ALTER TABLE pi_transfer
    ADD COLUMN IF NOT EXISTS reference_bulk VARCHAR(35);

-- Gap 4: Cashback monetary breakdown (pp.73-74) — needed for merchant canals
-- (500, 521, 520, 400) in send_now as well as RTP acceptance.
ALTER TABLE pi_transfer
    ADD COLUMN IF NOT EXISTS montant_achat    NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS montant_retrait  NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS frais_retrait    NUMERIC(18, 2);

-- Gap 5: Document reference (p.73) — needed for B2B/invoice-driven send_now.
ALTER TABLE pi_transfer
    ADD COLUMN IF NOT EXISTS type_document_reference    VARCHAR(10),
    ADD COLUMN IF NOT EXISTS numero_document_reference  VARCHAR(35);
