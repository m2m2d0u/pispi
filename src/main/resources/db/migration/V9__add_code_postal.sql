-- code_postal is accepted on creation/modification payloads (codePostaleClient /
-- codePostalClient) but was never persisted. Add a column so the entity can
-- store it and modify/search flows can round-trip it correctly.
ALTER TABLE pi_alias ADD COLUMN code_postal VARCHAR(20);
