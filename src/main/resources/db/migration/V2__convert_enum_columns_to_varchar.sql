-- ============================================================================
-- PI-SPI V2: Convert PostgreSQL custom enum columns to VARCHAR
-- Reason: Hibernate @Enumerated(EnumType.STRING) sends java.lang.String (varchar)
--         but PostgreSQL requires an explicit cast to a custom enum type,
--         causing "column X is of type foo_enum but expression is of type
--         character varying" errors at runtime.
-- Solution: Convert all custom enum columns to VARCHAR so that standard
--           @Enumerated(EnumType.STRING) bindings work without any extra
--           converters or Hibernate-specific annotations.
-- NOTE: Partial indexes whose WHERE clause references an enum column must be
--       dropped before ALTER COLUMN TYPE and recreated afterwards, because
--       PostgreSQL stores the condition with the original enum type and cannot
--       rebuild the index when the column changes to VARCHAR.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 1: Drop partial indexes whose WHERE clause uses a custom enum column
-- ----------------------------------------------------------------------------

-- idx_rtp_limite: WHERE statut = 'PENDING' (statut was rtp_status)
DROP INDEX IF EXISTS idx_rtp_limite;

-- idx_alias_active: WHERE statut = 'ACTIVE' (statut was alias_status)
DROP INDEX IF EXISTS idx_alias_active;

-- ----------------------------------------------------------------------------
-- Step 2: Convert all custom enum columns to VARCHAR
-- ----------------------------------------------------------------------------

-- pi_message_log
ALTER TABLE pi_message_log
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text,
    ALTER COLUMN message_type TYPE VARCHAR(30)  USING message_type::text;

-- pi_transfer
ALTER TABLE pi_transfer
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text,
    ALTER COLUMN statut       TYPE VARCHAR(10)  USING statut::text;

ALTER TABLE pi_transfer
    ALTER COLUMN statut SET DEFAULT 'PEND';

-- pi_identity_verification
ALTER TABLE pi_identity_verification
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text,
    ALTER COLUMN statut       TYPE VARCHAR(20)  USING statut::text;

ALTER TABLE pi_identity_verification
    ALTER COLUMN statut SET DEFAULT 'PENDING';

-- pi_request_to_pay
ALTER TABLE pi_request_to_pay
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text,
    ALTER COLUMN statut       TYPE VARCHAR(10)  USING statut::text;

ALTER TABLE pi_request_to_pay
    ALTER COLUMN statut SET DEFAULT 'PENDING';

-- pi_return_request
ALTER TABLE pi_return_request
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text,
    ALTER COLUMN statut       TYPE VARCHAR(20)  USING statut::text;

ALTER TABLE pi_return_request
    ALTER COLUMN statut SET DEFAULT 'PENDING';

-- pi_return_execution
ALTER TABLE pi_return_execution
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text;

-- pi_alias
ALTER TABLE pi_alias
    ALTER COLUMN statut       TYPE VARCHAR(20)  USING statut::text;

ALTER TABLE pi_alias
    ALTER COLUMN statut SET DEFAULT 'ACTIVE';

-- pi_alias_revendication
ALTER TABLE pi_alias_revendication
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text,
    ALTER COLUMN statut       TYPE VARCHAR(20)  USING statut::text;

ALTER TABLE pi_alias_revendication
    ALTER COLUMN statut SET DEFAULT 'INITIEE';

-- pi_notification
ALTER TABLE pi_notification
    ALTER COLUMN direction    TYPE VARCHAR(20)  USING direction::text;

-- ----------------------------------------------------------------------------
-- Step 3: Drop the now-unused custom enum types
-- ----------------------------------------------------------------------------

DROP TYPE message_direction;
DROP TYPE iso_message_type;
DROP TYPE transfer_status;
DROP TYPE rtp_status;
DROP TYPE alias_status;
DROP TYPE verification_status;
DROP TYPE return_request_status;
DROP TYPE revendication_status;

-- ----------------------------------------------------------------------------
-- Step 4: Recreate partial indexes (now using plain VARCHAR comparisons)
-- ----------------------------------------------------------------------------

CREATE INDEX idx_rtp_limite ON pi_request_to_pay (date_heure_limite_action)
    WHERE date_heure_limite_action IS NOT NULL AND statut = 'PENDING';

CREATE UNIQUE INDEX idx_alias_active ON pi_alias (alias_value, type_alias)
    WHERE statut = 'ACTIVE';
