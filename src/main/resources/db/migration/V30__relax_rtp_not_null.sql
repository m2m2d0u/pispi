-- Relax legacy NOT NULL constraints on pi_request_to_pay that the BCEAO
-- DemandePaiement (PAIN.013) schema does not always populate for inbound RTPs.
-- V19 already dropped type_transaction, numero_compte_paye, type_compte_paye,
-- and date_heure_execution. This migration drops the rest that the entity does
-- not enforce as nullable=false.
ALTER TABLE pi_request_to_pay
    ALTER COLUMN code_membre_payeur  DROP NOT NULL,
    ALTER COLUMN nom_client_payeur   DROP NOT NULL,
    ALTER COLUMN type_client_payeur  DROP NOT NULL,
    ALTER COLUMN canal_communication DROP NOT NULL,
    ALTER COLUMN montant             DROP NOT NULL,
    ALTER COLUMN code_membre_paye    DROP NOT NULL,
    ALTER COLUMN nom_client_paye     DROP NOT NULL,
    ALTER COLUMN type_client_paye    DROP NOT NULL;
