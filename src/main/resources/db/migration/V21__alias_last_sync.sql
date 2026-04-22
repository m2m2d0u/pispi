-- BCEAO PI-RAC §4.4 — track the last synchronization point so the participant
-- can compare against its own back-office timestamp and push modifications
-- when the stored alias data is stale.
--
-- Seeded to date_modification_rac for existing rows (or created_at if the
-- modification date is null) so the first diff cycle after migration treats
-- everything as in-sync.

ALTER TABLE pi_alias
    ADD COLUMN date_last_sync TIMESTAMPTZ;

UPDATE pi_alias
SET date_last_sync = COALESCE(date_modification_rac, date_creation_rac, created_at)
WHERE date_last_sync IS NULL;
