-- Track the revendication timeline explicitly so the scheduler can automate
-- the day-7 lock and day-14 auto-acceptance per BCEAO PI-RAC §3.
--
-- Note: date_verrouillage already exists in V1__init_schema.sql (pi_alias_revendication);
-- we only add the missing date_initiation column here plus a covering index
-- for the scheduler queries.

ALTER TABLE pi_alias_revendication
    ADD COLUMN date_initiation TIMESTAMPTZ;

-- Backfill date_initiation from created_at for historical rows so the
-- scheduler treats them consistently.
UPDATE pi_alias_revendication
SET date_initiation = created_at
WHERE date_initiation IS NULL;

-- Covering index for the scheduler query (find INITIEE rows older than the cutoff).
CREATE INDEX IF NOT EXISTS idx_rev_statut_initiation
    ON pi_alias_revendication (statut, date_initiation)
    WHERE statut = 'INITIEE';

-- v3.0.0 workflow: RevendicationService.initiateClaim now persists an OUTBOUND
-- placeholder row with statut=INITIEE the moment the claim is sent to the AIP,
-- so we can reconcile against the AIP's identifiantRevendication on the callback.
-- At that moment detenteur is unknown (only the AIP can tell us who holds the
-- alias) and revendicateur for INBOUND claims is only known after the AIP pushes
-- the notification. Relax both to NOT NULL → nullable.
ALTER TABLE pi_alias_revendication
    ALTER COLUMN detenteur     DROP NOT NULL,
    ALTER COLUMN revendicateur DROP NOT NULL;

