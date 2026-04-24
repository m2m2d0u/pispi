-- Drop unused columns from pi_request_to_pay.
--
-- Three categories:
--   a) V1 columns superseded by V19 equivalents that the entity already maps
--      (code_systeme_id_* → systeme_identification_*, identifiant_* →
--       numero_identification_*, code_type_document → type_document_reference,
--       identifiant_document → numero_document_reference)
--   b) V1 columns that were never integrated into the entity (prenom, adresse,
--       date_heure_execution, reference_client, libelle_document, montant/frais
--       fields, mandat fields, informations_additionnelles)
--   c) Columns that were mapped in the entity but explicitly marked as legacy
--      and never populated by any new code path (type_transaction,
--      numero_compte_payeur, numero_compte_paye)
ALTER TABLE pi_request_to_pay
    DROP COLUMN IF EXISTS type_transaction,
    DROP COLUMN IF EXISTS date_heure_execution,
    DROP COLUMN IF EXISTS numero_compte_payeur,
    DROP COLUMN IF EXISTS prenom_client_payeur,
    DROP COLUMN IF EXISTS code_systeme_id_payeur,
    DROP COLUMN IF EXISTS identifiant_payeur,
    DROP COLUMN IF EXISTS adresse_payeur,
    DROP COLUMN IF EXISTS numero_compte_paye,
    DROP COLUMN IF EXISTS prenom_client_paye,
    DROP COLUMN IF EXISTS code_systeme_id_paye,
    DROP COLUMN IF EXISTS identifiant_paye,
    DROP COLUMN IF EXISTS adresse_paye,
    DROP COLUMN IF EXISTS reference_client,
    DROP COLUMN IF EXISTS code_type_document,
    DROP COLUMN IF EXISTS identifiant_document,
    DROP COLUMN IF EXISTS libelle_document,
    DROP COLUMN IF EXISTS montant_achat,
    DROP COLUMN IF EXISTS montant_retrait,
    DROP COLUMN IF EXISTS frais_retrait,
    DROP COLUMN IF EXISTS montant_remise_paiement_immediat,
    DROP COLUMN IF EXISTS taux_remise_paiement_immediat,
    DROP COLUMN IF EXISTS identifiant_mandat,
    DROP COLUMN IF EXISTS signature_numerique_mandat,
    DROP COLUMN IF EXISTS informations_additionnelles;
