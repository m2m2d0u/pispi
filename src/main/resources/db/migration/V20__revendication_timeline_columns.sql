-- Track the revendication timeline explicitly so the scheduler can
-- automate the day-7 lock and day-14 auto-acceptance per BCEAO PI-RAC §3.

ALTER TABLE pi_alias_revendication
    ADD COLUMN date_initiation    TIMESTAMPTZ,
    ADD COLUMN date_verrouillage  TIMESTAMPTZ;

-- Backfill date_initiation from created_at for historical rows so the
-- scheduler treats them consistently.
UPDATE pi_alias_revendication
SET date_initiation = created_at
WHERE date_initiation IS NULL;

-- Index for the scheduler query (find INITIEE rows older than the cutoff).
CREATE INDEX idx_rev_statut_initiation
    ON pi_alias_revendication (statut, date_initiation)
    WHERE statut = 'INITIEE';
