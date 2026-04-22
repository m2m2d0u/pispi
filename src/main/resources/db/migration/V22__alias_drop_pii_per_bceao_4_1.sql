-- BCEAO PI-RAC v3.0.0 §4.1 — "Les participants NE DOIVENT PAS répliquer la
-- base des alias dans leur système. [...] Les participants peuvent stocker
-- uniquement l'alias de leur client au moment de la création de l'alias."
--
-- Bring pi_alias in line with §4.1 by:
--   1) adding back_office_client_id (opaque reference to the participant's
--      own client system) so we can group an MBNO+SHID pair without needing
--      to replicate the national identification number here.
--   2) dropping every PII / redundant-with-PI-RAC column. The AliasCreationRequest
--      DTO still carries these fields — they are forwarded to PI-RAC but not
--      persisted locally. At read time, the participant reads from its own
--      back office (by back_office_client_id) or performs a fresh RAC_SEARCH.
--
-- Operational codes that describe the alias itself (typeAlias, typeClient,
-- typeIdentifiant, typeCompte, numeroCompte) remain — they are needed by the
-- local duplicate / cascade / UI display paths and are not personal data.

-- ---------------------------------------------------------------------------
-- 1) Add the opaque back-office reference.
-- ---------------------------------------------------------------------------
ALTER TABLE pi_alias
    ADD COLUMN back_office_client_id VARCHAR(64);

-- Best-effort backfill: historical rows used `identifiant` as the linking key.
-- Copy its value into back_office_client_id so the MBNO+SHID cascade keeps
-- working during the transition. Rows with no identifiant (TRAL, legacy)
-- stay NULL and the caller must repopulate on the next modification.
UPDATE pi_alias
SET back_office_client_id = identifiant
WHERE back_office_client_id IS NULL
  AND identifiant IS NOT NULL;

CREATE INDEX idx_alias_back_office_client
    ON pi_alias (back_office_client_id)
    WHERE back_office_client_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2) Drop PII columns.
-- ---------------------------------------------------------------------------
ALTER TABLE pi_alias
    DROP COLUMN nom,
    DROP COLUMN prenom,
    DROP COLUMN lieu_naissance,
    DROP COLUMN pays_naissance,
    DROP COLUMN date_naissance,
    DROP COLUMN nationalite,
    DROP COLUMN pays,
    DROP COLUMN adresse,
    DROP COLUMN ville,
    DROP COLUMN code_postal,
    DROP COLUMN telephone,
    DROP COLUMN email,
    DROP COLUMN raison_sociale,
    DROP COLUMN identifiant,
    DROP COLUMN code_marchand,
    DROP COLUMN categorie_code_marchand,
    DROP COLUMN nom_marchand;

-- Drop the old index that depended on identifiant (if one existed — IF EXISTS
-- is safe on PostgreSQL 9+).
DROP INDEX IF EXISTS idx_alias_identifiant;
