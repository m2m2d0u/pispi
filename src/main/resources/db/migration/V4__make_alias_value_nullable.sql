-- Make alias_value nullable for SHID aliases (PI-RAC generates the value)
ALTER TABLE pi_alias ALTER COLUMN alias_value DROP NOT NULL;
