-- Relax NOT NULL constraints on pi_transfer columns that may be absent in inbound PACS.008 callbacks
ALTER TABLE pi_transfer
    ALTER COLUMN numero_compte_payeur DROP NOT NULL,
    ALTER COLUMN type_compte_payeur   DROP NOT NULL,
    ALTER COLUMN type_client_payeur   DROP NOT NULL,
    ALTER COLUMN numero_compte_paye   DROP NOT NULL,
    ALTER COLUMN type_compte_paye     DROP NOT NULL,
    ALTER COLUMN type_client_paye     DROP NOT NULL,
    ALTER COLUMN nom_client_payeur    DROP NOT NULL,
    ALTER COLUMN nom_client_paye      DROP NOT NULL;
