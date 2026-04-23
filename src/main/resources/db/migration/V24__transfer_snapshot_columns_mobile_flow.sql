-- Phase 3.1: snapshot columns on pi_transfer for the mobile-facing two-phase
-- flow (POST /api/v1/transferts → PUT /api/v1/transferts/{id}).
--
-- The legacy single-shot flow built the PACS.008 in-memory from a freshly
-- resolved ResolvedClient, so it never needed these columns persisted. The
-- two-phase flow captures the full beneficiary + payer snapshot at initiation
-- and rebuilds the PACS.008 from the row at confirmation time — otherwise
-- a RAC_SEARCH log rotation between init and confirm would silently produce
-- stale identity data and trigger BE01 downstream.
--
-- Also carries the confirmation metadata (date, method, GPS) captured from
-- TransactionConfirmCommand, and a back-pointer to the RAC_SEARCH log entry
-- so audits can trace the source of the payee identity.

ALTER TABLE pi_transfer
    -- Payé — identity snapshot (beyond what V1 already persists)
    ADD COLUMN pays_client_paye                      VARCHAR(2),
    ADD COLUMN identifiant_client_paye               VARCHAR(35),
    ADD COLUMN type_identifiant_client_paye          VARCHAR(4),
    ADD COLUMN alias_paye                            VARCHAR(50),
    ADD COLUMN iban_client_paye                      VARCHAR(34),
    ADD COLUMN identification_fiscale_commercant_paye VARCHAR(35),
    ADD COLUMN identification_rccm_client_paye       VARCHAR(35),

    -- Payeur — identity snapshot
    ADD COLUMN pays_client_payeur                    VARCHAR(2),
    ADD COLUMN identifiant_client_payeur             VARCHAR(35),
    ADD COLUMN type_identifiant_client_payeur        VARCHAR(4),
    ADD COLUMN alias_payeur                          VARCHAR(50),
    ADD COLUMN iban_client_payeur                    VARCHAR(34),
    ADD COLUMN identification_fiscale_commercant_payeur VARCHAR(35),
    ADD COLUMN identification_rccm_client_payeur     VARCHAR(35),

    -- Confirmation metadata (PUT /transferts/{id})
    ADD COLUMN confirmation_date                     TIMESTAMP,
    ADD COLUMN confirmation_methode                  VARCHAR(10),
    ADD COLUMN latitude_client_payeur                VARCHAR(20),
    ADD COLUMN longitude_client_payeur               VARCHAR(20),

    -- Audit: link back to the RAC_SEARCH log entry that produced the payee
    -- snapshot. Uses the message log's endToEndId (50 chars per V5).
    ADD COLUMN rac_search_ref_paye                   VARCHAR(50),
    ADD COLUMN rac_search_ref_payeur                 VARCHAR(50);
