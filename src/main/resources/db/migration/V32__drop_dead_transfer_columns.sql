-- Re-add rtp_end_to_end_id which was dropped in V26 but is now needed to link
-- a pacs.008 transfer row back to the originating pain.013 RTP.
ALTER TABLE pi_transfer
    ADD COLUMN IF NOT EXISTS rtp_end_to_end_id VARCHAR(35);

CREATE INDEX IF NOT EXISTS idx_transfer_rtp
    ON pi_transfer (rtp_end_to_end_id)
    WHERE rtp_end_to_end_id IS NOT NULL;

-- Drop V1 pi_transfer columns superseded by V24/V28 snapshot equivalents
-- or never integrated into the entity model.
--
--   V1 column                   →  V24/V28 replacement in entity
--   code_systeme_id_payeur      →  type_identifiant_client_payeur
--   identifiant_payeur          →  identifiant_client_payeur
--   adresse_payeur              →  adresse_client_payeur
--   pays_payeur                 →  pays_client_payeur
--   code_systeme_id_paye        →  type_identifiant_client_paye
--   identifiant_paye            →  identifiant_client_paye
--   adresse_paye                →  adresse_client_paye
--   pays_paye                   →  pays_client_paye
--   code_type_document          →  (not persisted — carried in request DTO only)
--   identifiant_document        →  (not persisted)
--   libelle_document            →  (not persisted)
--   informations_additionnelles →  (not persisted)
ALTER TABLE pi_transfer
    DROP COLUMN IF EXISTS code_systeme_id_payeur,
    DROP COLUMN IF EXISTS identifiant_payeur,
    DROP COLUMN IF EXISTS adresse_payeur,
    DROP COLUMN IF EXISTS pays_payeur,
    DROP COLUMN IF EXISTS code_systeme_id_paye,
    DROP COLUMN IF EXISTS identifiant_paye,
    DROP COLUMN IF EXISTS adresse_paye,
    DROP COLUMN IF EXISTS pays_paye,
    DROP COLUMN IF EXISTS code_type_document,
    DROP COLUMN IF EXISTS identifiant_document,
    DROP COLUMN IF EXISTS libelle_document,
    DROP COLUMN IF EXISTS informations_additionnelles;
