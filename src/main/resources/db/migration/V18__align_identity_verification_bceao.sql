-- Align pi_identity_verification with BCEAO Identite / IdentiteReponse schemas.
-- See documentation/interface-participant-openapi.json (#/components/schemas/Identite
-- and #/components/schemas/IdentiteReponse).

-- 1) Add new BCEAO-spec columns.
ALTER TABLE pi_identity_verification
    ADD COLUMN code_membre_participant            VARCHAR(6),
    ADD COLUMN iban_client                        VARCHAR(34),
    ADD COLUMN other_client                       VARCHAR(70),
    ADD COLUMN type_client                        VARCHAR(1),
    ADD COLUMN type_compte                        VARCHAR(4),
    ADD COLUMN nom_client                         VARCHAR(140),
    ADD COLUMN ville_client                       VARCHAR(140),
    ADD COLUMN adresse_complete                   VARCHAR(350),
    ADD COLUMN numero_identification              VARCHAR(35),
    ADD COLUMN systeme_identification             VARCHAR(4),
    ADD COLUMN numero_rccm_client                 VARCHAR(35),
    ADD COLUMN identification_fiscale_commercant  VARCHAR(35),
    ADD COLUMN date_naissance                     DATE,
    ADD COLUMN ville_naissance                    VARCHAR(140),
    ADD COLUMN pays_naissance                     VARCHAR(2),
    ADD COLUMN pays_residence                     VARCHAR(2),
    ADD COLUMN devise                             VARCHAR(3);

-- 2) Relax NOT NULL constraints on legacy columns that are no longer populated
--    by the BCEAO-aligned flow. The columns are preserved for historical data
--    but future rows may leave them NULL.
ALTER TABLE pi_identity_verification
    ALTER COLUMN code_membre_payeur   DROP NOT NULL,
    ALTER COLUMN code_membre_paye     DROP NOT NULL,
    ALTER COLUMN numero_compte_paye   DROP NOT NULL,
    ALTER COLUMN type_compte_paye     DROP NOT NULL,
    ALTER COLUMN nom_client_paye      DROP NOT NULL;
