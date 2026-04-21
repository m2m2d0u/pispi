-- MBNO creation provisions both an MBNO and a SHID alias that share a single
-- idCreationAlias (endToEndId) end-to-end. To allow two rows with the same
-- end_to_end_id (one per typeAlias), relax uniqueness from end_to_end_id alone
-- to (end_to_end_id, type_alias).
DROP INDEX IF EXISTS idx_alias_e2e;
CREATE UNIQUE INDEX idx_alias_e2e_type
    ON pi_alias (end_to_end_id, type_alias);

-- Uniqueness constraint requested: the triple (identifiant, type_alias, statut)
-- must be unique. Replaces the V7 index which only covered ACTIVE rows.
DROP INDEX IF EXISTS idx_alias_identifiant_type_active;
CREATE UNIQUE INDEX idx_alias_identifiant_type_statut
    ON pi_alias (identifiant, type_alias, statut)
    WHERE identifiant IS NOT NULL;
