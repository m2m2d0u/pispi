-- Enforce that a client (identifiant) can hold at most one ACTIVE alias of a given type.
-- Partial index: only applies when both columns are non-null and the alias is ACTIVE.
CREATE UNIQUE INDEX idx_alias_identifiant_type_active
    ON pi_alias (identifiant, type_alias)
    WHERE statut = 'ACTIVE' AND identifiant IS NOT NULL;
