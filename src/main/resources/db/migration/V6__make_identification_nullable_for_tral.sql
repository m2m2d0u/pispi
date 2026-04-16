-- Allow null type_identifiant and identifiant for TRAL accounts (persons without identification)
ALTER TABLE pi_alias ALTER COLUMN type_identifiant DROP NOT NULL;
ALTER TABLE pi_alias ALTER COLUMN identifiant DROP NOT NULL;
