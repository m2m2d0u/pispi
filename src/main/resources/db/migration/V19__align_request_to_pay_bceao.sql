-- Align pi_request_to_pay with BCEAO DemandePaiement (PAIN.013) schema.
-- See documentation/interface-participant-openapi.json
-- #/components/schemas/DemandePaiement.

-- 1) Add the alias columns (REQUIRED by BCEAO schema — aliasClientPayeur &
--    aliasClientPaye are in the top-level "required" list).
ALTER TABLE pi_request_to_pay
    ADD COLUMN alias_client_payeur  VARCHAR(140),
    ADD COLUMN alias_client_paye    VARCHAR(140);

-- 2) Split the single numero_compte_* column into iban/other per BCEAO.
ALTER TABLE pi_request_to_pay
    ADD COLUMN iban_client_payeur   VARCHAR(34),
    ADD COLUMN other_client_payeur  VARCHAR(70),
    ADD COLUMN iban_client_paye     VARCHAR(34),
    ADD COLUMN other_client_paye    VARCHAR(70);

-- 3) Additional BCEAO-spec columns not previously captured.
ALTER TABLE pi_request_to_pay
    ADD COLUMN ville_client_payeur                VARCHAR(140),
    ADD COLUMN ville_client_paye                  VARCHAR(140),
    ADD COLUMN numero_identification_payeur       VARCHAR(35),
    ADD COLUMN numero_identification_paye         VARCHAR(35),
    ADD COLUMN systeme_identification_payeur      VARCHAR(4),
    ADD COLUMN systeme_identification_paye        VARCHAR(4),
    ADD COLUMN numero_rccm_payeur                 VARCHAR(35),
    ADD COLUMN numero_rccm_paye                   VARCHAR(35),
    ADD COLUMN date_naissance_payeur              VARCHAR(10),
    ADD COLUMN date_naissance_paye                VARCHAR(10),
    ADD COLUMN ville_naissance_payeur             VARCHAR(140),
    ADD COLUMN ville_naissance_paye               VARCHAR(140),
    ADD COLUMN pays_naissance_payeur              VARCHAR(2),
    ADD COLUMN pays_naissance_paye                VARCHAR(2),
    ADD COLUMN identification_fiscale_payeur      VARCHAR(35),
    ADD COLUMN identification_fiscale_paye        VARCHAR(35),
    ADD COLUMN latitude_client_paye               VARCHAR(20),
    ADD COLUMN longitude_client_paye              VARCHAR(20),
    ADD COLUMN type_document_reference            VARCHAR(4),
    ADD COLUMN numero_document_reference          VARCHAR(35);

-- 4) Relax NOT NULL on columns that the BCEAO schema does not require or
--    that we no longer populate (type_transaction is not a BCEAO field;
--    numero_compte_paye is replaced by iban_client_paye / other_client_paye).
ALTER TABLE pi_request_to_pay
    ALTER COLUMN type_transaction      DROP NOT NULL,
    ALTER COLUMN numero_compte_paye    DROP NOT NULL,
    ALTER COLUMN type_compte_paye      DROP NOT NULL,
    ALTER COLUMN date_heure_execution  DROP NOT NULL;
