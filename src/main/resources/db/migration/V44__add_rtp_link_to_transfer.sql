-- No-op idempotent. La colonne {@code rtp_end_to_end_id} et son index partiel
-- ont en réalité été créés par V32 (ré-introduction après le drop de V26).
-- Cette migration V44 a été rédigée par erreur sans avoir vu V32 — elle
-- existe pour satisfaire l'historique Flyway dans les déploiements où elle
-- a été tentée puis a échoué (rollback) avant la correction.
--
-- {@code IF NOT EXISTS} garantit qu'elle reste neutre quel que soit l'ordre :
--   - Sur un schéma déjà passé par V32 : skip silencieux.
--   - Sur un schéma jamais passé par V32 (théoriquement impossible vu
--     l'ordre des numéros, mais filet de sécurité) : crée la colonne et
--     l'index avec le nom historique {@code idx_transfer_rtp}.

ALTER TABLE pi_transfer
    ADD COLUMN IF NOT EXISTS rtp_end_to_end_id VARCHAR(35);

CREATE INDEX IF NOT EXISTS idx_transfer_rtp
    ON pi_transfer (rtp_end_to_end_id)
    WHERE rtp_end_to_end_id IS NOT NULL;
