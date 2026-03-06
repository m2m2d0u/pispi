-- ============================================================================
-- PI-SPI Connector Database Schema
-- Flyway Migration V1: Initial Schema
-- PostgreSQL 15+
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Custom ENUM Types
-- ----------------------------------------------------------------------------

CREATE TYPE message_direction AS ENUM ('OUTBOUND', 'INBOUND');

CREATE TYPE iso_message_type AS ENUM (
    'PACS_008', 'PACS_002', 'PACS_028', 'PACS_004',
    'PAIN_013', 'PAIN_014',
    'ACMT_023', 'ACMT_024',
    'CAMT_013', 'CAMT_014', 'CAMT_029', 'CAMT_056', 'CAMT_060',
    'CAMT_052', 'CAMT_053', 'CAMT_010', 'CAMT_086',
    'ADMI_002', 'ADMI_004', 'ADMI_011',
    'REDA_017',
    'RAC_CREATE', 'RAC_MODIFY', 'RAC_DELETE', 'RAC_SEARCH',
    'RAC_REVENDICATION'
);

CREATE TYPE transfer_status AS ENUM ('PEND', 'ACCC', 'ACSC', 'ACSP', 'RJCT', 'TMOT');
CREATE TYPE rtp_status AS ENUM ('PENDING', 'ACCEPTED', 'RJCT', 'EXPIRED', 'TMOT');
CREATE TYPE alias_status AS ENUM ('ACTIVE', 'MODIFIED', 'DELETED', 'FAILED');
CREATE TYPE verification_status AS ENUM ('PENDING', 'VERIFIED', 'FAILED', 'TIMEOUT');
CREATE TYPE return_request_status AS ENUM ('PENDING', 'ACCEPTED', 'RJCR', 'TIMEOUT');
CREATE TYPE revendication_status AS ENUM ('INITIEE', 'ACCEPTEE', 'REJETEE', 'ECHEC');

-- ----------------------------------------------------------------------------
-- 2. Utility: auto-update updated_at trigger
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- 3. pi_message_log - Immutable audit trail
-- ----------------------------------------------------------------------------

CREATE TABLE pi_message_log (
    id              BIGSERIAL       PRIMARY KEY,
    msg_id          VARCHAR(35)     NOT NULL,
    msg_id_demande  VARCHAR(35),
    end_to_end_id   VARCHAR(35),
    message_type    iso_message_type NOT NULL,
    direction       message_direction NOT NULL,
    payload         JSONB           NOT NULL,
    http_status     INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_msg_log_msg_id ON pi_message_log (msg_id);
CREATE INDEX idx_msg_log_e2e ON pi_message_log (end_to_end_id);
CREATE INDEX idx_msg_log_type_dir ON pi_message_log (message_type, direction);
CREATE INDEX idx_msg_log_created ON pi_message_log (created_at);

-- ----------------------------------------------------------------------------
-- 4. pi_transfer - PACS.008 / PACS.002
-- ----------------------------------------------------------------------------

CREATE TABLE pi_transfer (
    id                          BIGSERIAL       PRIMARY KEY,
    msg_id                      VARCHAR(35)     NOT NULL,
    end_to_end_id               VARCHAR(35)     NOT NULL,
    direction                   message_direction NOT NULL,
    type_transaction            VARCHAR(4)      NOT NULL,
    canal_communication         VARCHAR(3)      NOT NULL,
    montant                     DECIMAL(18,2)   NOT NULL CHECK (montant > 0),
    devise                      VARCHAR(3)      NOT NULL DEFAULT 'XOF',
    date_heure_execution        TIMESTAMPTZ     NOT NULL,

    -- Payer details
    code_membre_payeur          VARCHAR(6)      NOT NULL,
    numero_compte_payeur        VARCHAR(34)     NOT NULL,
    type_compte_payeur          VARCHAR(4)      NOT NULL,
    nom_client_payeur           VARCHAR(140)    NOT NULL,
    prenom_client_payeur        VARCHAR(140),
    type_client_payeur          VARCHAR(1)      NOT NULL,
    code_systeme_id_payeur      VARCHAR(4),
    identifiant_payeur          VARCHAR(35),
    adresse_payeur              VARCHAR(140),
    pays_payeur                 VARCHAR(2),
    telephone_payeur            VARCHAR(20),

    -- Payee details
    code_membre_paye            VARCHAR(6)      NOT NULL,
    numero_compte_paye          VARCHAR(34)     NOT NULL,
    type_compte_paye            VARCHAR(4)      NOT NULL,
    nom_client_paye             VARCHAR(140)    NOT NULL,
    prenom_client_paye          VARCHAR(140),
    type_client_paye            VARCHAR(1)      NOT NULL,
    code_systeme_id_paye        VARCHAR(4),
    identifiant_paye            VARCHAR(35),
    adresse_paye                VARCHAR(140),
    pays_paye                   VARCHAR(2),
    telephone_paye              VARCHAR(20),

    -- Transaction details
    motif                       VARCHAR(140),
    reference_client            VARCHAR(35),
    code_type_document          VARCHAR(4),
    identifiant_document        VARCHAR(35),
    libelle_document            VARCHAR(140),
    montant_achat               DECIMAL(18,2),
    montant_retrait             DECIMAL(18,2),
    frais_retrait               DECIMAL(18,2),
    informations_additionnelles TEXT,

    -- Status tracking
    statut                      transfer_status NOT NULL DEFAULT 'PEND',
    code_raison                 VARCHAR(10),
    msg_id_reponse              VARCHAR(35),
    date_heure_irrevocabilite   TIMESTAMPTZ,

    -- Link to originating RTP
    rtp_end_to_end_id           VARCHAR(35),

    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_transfer_msg_id ON pi_transfer (msg_id);
CREATE UNIQUE INDEX idx_transfer_e2e ON pi_transfer (end_to_end_id);
CREATE INDEX idx_transfer_statut ON pi_transfer (direction, statut);
CREATE INDEX idx_transfer_payeur ON pi_transfer (code_membre_payeur);
CREATE INDEX idx_transfer_paye ON pi_transfer (code_membre_paye);
CREATE INDEX idx_transfer_created ON pi_transfer (created_at);
CREATE INDEX idx_transfer_rtp ON pi_transfer (rtp_end_to_end_id) WHERE rtp_end_to_end_id IS NOT NULL;

CREATE TRIGGER trg_transfer_updated_at
    BEFORE UPDATE ON pi_transfer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 5. pi_identity_verification - ACMT.023 / ACMT.024
-- ----------------------------------------------------------------------------

CREATE TABLE pi_identity_verification (
    id                      BIGSERIAL           PRIMARY KEY,
    msg_id                  VARCHAR(35)         NOT NULL,
    end_to_end_id           VARCHAR(35)         NOT NULL,
    direction               message_direction   NOT NULL,
    code_membre_payeur      VARCHAR(6)          NOT NULL,
    code_membre_paye        VARCHAR(6)          NOT NULL,
    numero_compte_paye      VARCHAR(34)         NOT NULL,
    type_compte_paye        VARCHAR(4)          NOT NULL,
    nom_client_paye         VARCHAR(140)        NOT NULL,
    prenom_client_paye      VARCHAR(140),
    resultat_verification   BOOLEAN,
    code_raison             VARCHAR(10),
    msg_id_reponse          VARCHAR(35),
    statut                  verification_status NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_verif_msg_id ON pi_identity_verification (msg_id);
CREATE UNIQUE INDEX idx_verif_e2e ON pi_identity_verification (end_to_end_id);

CREATE TRIGGER trg_verification_updated_at
    BEFORE UPDATE ON pi_identity_verification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 6. pi_request_to_pay - PAIN.013 / PAIN.014
-- ----------------------------------------------------------------------------

CREATE TABLE pi_request_to_pay (
    id                                  BIGSERIAL       PRIMARY KEY,
    msg_id                              VARCHAR(35)     NOT NULL,
    end_to_end_id                       VARCHAR(35)     NOT NULL,
    identifiant_demande_paiement        VARCHAR(35),
    reference_bulk                      VARCHAR(35),
    direction                           message_direction NOT NULL,
    type_transaction                    VARCHAR(4)      NOT NULL,
    canal_communication                 VARCHAR(3)      NOT NULL,
    montant                             DECIMAL(18,2)   NOT NULL CHECK (montant > 0),
    devise                              VARCHAR(3)      NOT NULL DEFAULT 'XOF',
    date_heure_execution                TIMESTAMPTZ     NOT NULL,
    date_heure_limite_action            TIMESTAMPTZ,

    -- Payer details
    code_membre_payeur                  VARCHAR(6)      NOT NULL,
    numero_compte_payeur                VARCHAR(34),
    type_compte_payeur                  VARCHAR(4),
    nom_client_payeur                   VARCHAR(140)    NOT NULL,
    prenom_client_payeur                VARCHAR(140),
    type_client_payeur                  VARCHAR(1)      NOT NULL,
    code_systeme_id_payeur              VARCHAR(4),
    identifiant_payeur                  VARCHAR(35),
    adresse_payeur                      VARCHAR(140),
    pays_payeur                         VARCHAR(2),
    telephone_payeur                    VARCHAR(20),

    -- Payee details
    code_membre_paye                    VARCHAR(6)      NOT NULL,
    numero_compte_paye                  VARCHAR(34)     NOT NULL,
    type_compte_paye                    VARCHAR(4)      NOT NULL,
    nom_client_paye                     VARCHAR(140)    NOT NULL,
    prenom_client_paye                  VARCHAR(140),
    type_client_paye                    VARCHAR(1)      NOT NULL,
    code_systeme_id_paye                VARCHAR(4),
    identifiant_paye                    VARCHAR(35),
    adresse_paye                        VARCHAR(140),
    pays_paye                           VARCHAR(2),
    telephone_paye                      VARCHAR(20),

    -- Transaction details
    motif                               VARCHAR(140),
    reference_client                    VARCHAR(35),
    code_type_document                  VARCHAR(4),
    identifiant_document                VARCHAR(35),
    libelle_document                    VARCHAR(140),
    montant_achat                       DECIMAL(18,2),
    montant_retrait                     DECIMAL(18,2),
    frais_retrait                       DECIMAL(18,2),
    autorisation_modification_montant   BOOLEAN,
    montant_remise_paiement_immediat    DECIMAL(18,2),
    taux_remise_paiement_immediat       DECIMAL(5,2),
    identifiant_mandat                  VARCHAR(35),
    signature_numerique_mandat          TEXT,
    informations_additionnelles         TEXT,

    -- Status tracking
    statut                              rtp_status      NOT NULL DEFAULT 'PENDING',
    code_raison                         VARCHAR(10),
    msg_id_reponse                      VARCHAR(35),
    transfer_end_to_end_id              VARCHAR(35),

    created_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_rtp_msg_id ON pi_request_to_pay (msg_id);
CREATE UNIQUE INDEX idx_rtp_e2e ON pi_request_to_pay (end_to_end_id);
CREATE INDEX idx_rtp_statut ON pi_request_to_pay (direction, statut);
CREATE INDEX idx_rtp_limite ON pi_request_to_pay (date_heure_limite_action)
    WHERE date_heure_limite_action IS NOT NULL AND statut = 'PENDING';
CREATE INDEX idx_rtp_transfer ON pi_request_to_pay (transfer_end_to_end_id)
    WHERE transfer_end_to_end_id IS NOT NULL;

CREATE TRIGGER trg_rtp_updated_at
    BEFORE UPDATE ON pi_request_to_pay
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 7. pi_return_request - CAMT.056 / CAMT.029
-- ----------------------------------------------------------------------------

CREATE TABLE pi_return_request (
    id                      BIGSERIAL               PRIMARY KEY,
    msg_id                  VARCHAR(35)             NOT NULL,
    identifiant_demande     VARCHAR(35)             NOT NULL,
    end_to_end_id           VARCHAR(35)             NOT NULL,
    direction               message_direction       NOT NULL,
    code_membre_payeur      VARCHAR(6)              NOT NULL,
    code_membre_paye        VARCHAR(6)              NOT NULL,
    raison                  VARCHAR(10)             NOT NULL,
    statut                  return_request_status   NOT NULL DEFAULT 'PENDING',
    raison_rejet            VARCHAR(10),
    msg_id_rejet            VARCHAR(35),
    created_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_return_req_msg_id ON pi_return_request (msg_id);
CREATE UNIQUE INDEX idx_return_req_demande ON pi_return_request (identifiant_demande);
CREATE INDEX idx_return_req_e2e ON pi_return_request (end_to_end_id);

CREATE TRIGGER trg_return_request_updated_at
    BEFORE UPDATE ON pi_return_request
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 8. pi_return_execution - PACS.004
-- ----------------------------------------------------------------------------

CREATE TABLE pi_return_execution (
    id                  BIGSERIAL           PRIMARY KEY,
    msg_id              VARCHAR(35)         NOT NULL,
    end_to_end_id       VARCHAR(35)         NOT NULL,
    direction           message_direction   NOT NULL,
    montant_retourne    DECIMAL(18,2)       NOT NULL CHECK (montant_retourne > 0),
    raison_retour       VARCHAR(10)         NOT NULL,
    return_request_id   BIGINT              REFERENCES pi_return_request(id),
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_return_exec_msg_id ON pi_return_execution (msg_id);
CREATE INDEX idx_return_exec_e2e ON pi_return_execution (end_to_end_id);

-- ----------------------------------------------------------------------------
-- 9. pi_alias - RAC alias management
-- ----------------------------------------------------------------------------

CREATE TABLE pi_alias (
    id                          BIGSERIAL       PRIMARY KEY,
    end_to_end_id               VARCHAR(35)     NOT NULL,
    alias_value                 VARCHAR(50)     NOT NULL,
    type_alias                  VARCHAR(4)      NOT NULL,
    type_client                 VARCHAR(1)      NOT NULL,
    nom                         VARCHAR(140)    NOT NULL,
    prenom                      VARCHAR(140),
    autre_prenom                VARCHAR(140),
    raison_sociale              VARCHAR(140),
    type_identifiant            VARCHAR(4)      NOT NULL,
    identifiant                 VARCHAR(35)     NOT NULL,
    date_naissance              DATE,
    lieu_naissance              VARCHAR(140),
    nationalite                 VARCHAR(2),
    adresse                     VARCHAR(140),
    ville                       VARCHAR(140),
    pays                        VARCHAR(2)      NOT NULL,
    telephone                   VARCHAR(20)     NOT NULL,
    email                       VARCHAR(254),
    numero_compte               VARCHAR(34)     NOT NULL,
    type_compte                 VARCHAR(4)      NOT NULL,
    code_membre_participant     VARCHAR(6)      NOT NULL,
    code_agence                 VARCHAR(20),
    nom_agence                  VARCHAR(140),
    code_marchand               VARCHAR(10),
    categorie_code_marchand     VARCHAR(10),
    nom_marchand                VARCHAR(140),
    ville_marchand              VARCHAR(140),
    pays_marchand               VARCHAR(2),
    statut                      alias_status    NOT NULL DEFAULT 'ACTIVE',
    date_creation_rac           TIMESTAMPTZ,
    date_modification_rac       TIMESTAMPTZ,
    date_suppression_rac        TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_alias_e2e ON pi_alias (end_to_end_id);
CREATE UNIQUE INDEX idx_alias_active ON pi_alias (alias_value, type_alias) WHERE statut = 'ACTIVE';
CREATE INDEX idx_alias_compte ON pi_alias (numero_compte);
CREATE INDEX idx_alias_type ON pi_alias (type_alias, statut);

CREATE TRIGGER trg_alias_updated_at
    BEFORE UPDATE ON pi_alias
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Constraint: MCOD requires merchant fields
ALTER TABLE pi_alias ADD CONSTRAINT chk_alias_marchand
    CHECK (type_alias != 'MCOD' OR code_marchand IS NOT NULL);

-- Constraint: Business types require raison_sociale
ALTER TABLE pi_alias ADD CONSTRAINT chk_alias_business
    CHECK (type_client = 'P' OR raison_sociale IS NOT NULL);

-- ----------------------------------------------------------------------------
-- 10. pi_alias_revendication - Alias claims
-- ----------------------------------------------------------------------------

CREATE TABLE pi_alias_revendication (
    id                              BIGSERIAL               PRIMARY KEY,
    identifiant_revendication       VARCHAR(50)             NOT NULL,
    alias_value                     VARCHAR(50)             NOT NULL,
    direction                       message_direction       NOT NULL,
    detenteur                       VARCHAR(6)              NOT NULL,
    revendicateur                   VARCHAR(6)              NOT NULL,
    statut                          revendication_status    NOT NULL DEFAULT 'INITIEE',
    date_action                     TIMESTAMPTZ,
    auteur_action                   VARCHAR(11),
    date_verrouillage               TIMESTAMPTZ,
    raison_rejet                    TEXT,
    informations_additionnelles     TEXT,
    created_at                      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_revend_id ON pi_alias_revendication (identifiant_revendication);
CREATE INDEX idx_revend_alias ON pi_alias_revendication (alias_value);

CREATE TRIGGER trg_revendication_updated_at
    BEFORE UPDATE ON pi_alias_revendication
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 11. pi_participant - Cached participant directory (CAMT.014)
-- ----------------------------------------------------------------------------

CREATE TABLE pi_participant (
    id                      BIGSERIAL       PRIMARY KEY,
    code_membre             VARCHAR(6)      NOT NULL,
    nom                     VARCHAR(140)    NOT NULL,
    etat                    VARCHAR(4)      NOT NULL,
    type_participant        VARCHAR(1)      NOT NULL,
    pays                    VARCHAR(2)      NOT NULL,
    participant_sponsor     VARCHAR(6),
    last_synced_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_participant_code ON pi_participant (code_membre);
CREATE INDEX idx_participant_etat ON pi_participant (etat);

CREATE TRIGGER trg_participant_updated_at
    BEFORE UPDATE ON pi_participant
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 12. pi_notification - ADMI.004 / ADMI.011 log
-- ----------------------------------------------------------------------------

CREATE TABLE pi_notification (
    id                      BIGSERIAL           PRIMARY KEY,
    msg_id                  VARCHAR(35)         NOT NULL,
    msg_id_demande          VARCHAR(35),
    direction               message_direction   NOT NULL,
    evenement               VARCHAR(4)          NOT NULL,
    evenement_description   TEXT,
    evenement_date          TIMESTAMPTZ         NOT NULL,
    message_type            VARCHAR(10)         NOT NULL,
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_notif_msg_id ON pi_notification (msg_id);
CREATE INDEX idx_notif_created ON pi_notification (created_at);

-- ----------------------------------------------------------------------------
-- 13. pi_compensation - CAMT.053 compensation balances
-- ----------------------------------------------------------------------------

CREATE TABLE pi_compensation (
    id                      BIGSERIAL       PRIMARY KEY,
    msg_id                  VARCHAR(35)     NOT NULL,
    solde_id                VARCHAR(50)     NOT NULL,
    date_debut_compense     TIMESTAMPTZ     NOT NULL,
    date_fin_compense       TIMESTAMPTZ     NOT NULL,
    participant             VARCHAR(6)      NOT NULL,
    participant_sponsor     VARCHAR(6),
    balance_type            VARCHAR(4)      NOT NULL DEFAULT 'CLBD',
    montant                 DECIMAL(18,2)   NOT NULL,
    operation_type          VARCHAR(4)      NOT NULL,
    date_balance            TIMESTAMPTZ     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comp_period ON pi_compensation (date_debut_compense, date_fin_compense);
CREATE INDEX idx_comp_participant ON pi_compensation (participant);

-- ----------------------------------------------------------------------------
-- 14. pi_guarantee - CAMT.010 / REDA.017
-- ----------------------------------------------------------------------------

CREATE TABLE pi_guarantee (
    id                                  BIGSERIAL       PRIMARY KEY,
    msg_id                              VARCHAR(35)     NOT NULL,
    source_message_type                 VARCHAR(10)     NOT NULL,
    participant_sponsor                 VARCHAR(6),
    montant_garantie                    DECIMAL(18,2),
    montant_restant_garantie            DECIMAL(18,2),
    type_operation_garantie             VARCHAR(4),
    date_effective_garantie             TIMESTAMPTZ,
    montant_solde_compense              DECIMAL(18,2),
    montant_restant_solde_compense      DECIMAL(18,2),
    type_operation_solde                VARCHAR(4),
    date_effective_solde                TIMESTAMPTZ,
    montant_mobilise                    DECIMAL(18,2),
    type_operation_mobilisation         VARCHAR(4),
    date_effective_mobilisation         TIMESTAMPTZ,
    -- REDA.017 specific fields
    montant_garantie_plafond            DECIMAL(18,2),
    date_debut                          DATE,
    date_fin                            DATE,
    -- Nested data stored as JSON
    payload                             JSONB,
    created_at                          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_guarantee_created ON pi_guarantee (created_at);

-- ----------------------------------------------------------------------------
-- 15. pi_invoice - CAMT.086
-- ----------------------------------------------------------------------------

CREATE TABLE pi_invoice (
    id                      BIGSERIAL       PRIMARY KEY,
    msg_id                  VARCHAR(35)     NOT NULL,
    groupe_id               VARCHAR(28)     NOT NULL,
    statement_id            VARCHAR(35)     NOT NULL,
    sender_name             VARCHAR(140),
    sender_id               VARCHAR(35),
    receiver_name           VARCHAR(140),
    receiver_id             VARCHAR(35),
    date_debut_facture      DATE            NOT NULL,
    date_fin_facture        DATE            NOT NULL,
    date_creation           TIMESTAMPTZ     NOT NULL,
    statut                  VARCHAR(20),
    niveau_compte           VARCHAR(20),
    id_compte               VARCHAR(35),
    id_compte_debit         VARCHAR(35),
    compensation_method     VARCHAR(20),
    devise_compte           VARCHAR(3),
    contact_institution     VARCHAR(140),
    service_lines           JSONB           NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_period ON pi_invoice (date_debut_facture, date_fin_facture);
CREATE INDEX idx_invoice_groupe ON pi_invoice (groupe_id);

-- ----------------------------------------------------------------------------
-- 16. pi_transaction_report - CAMT.052
-- ----------------------------------------------------------------------------

CREATE TABLE pi_transaction_report (
    id                              BIGSERIAL       PRIMARY KEY,
    msg_id                          VARCHAR(35)     NOT NULL,
    identifiant_releve              VARCHAR(24)     NOT NULL,
    date_debut_compense             TIMESTAMPTZ     NOT NULL,
    date_fin_compense               TIMESTAMPTZ     NOT NULL,
    code_membre_participant         VARCHAR(6)      NOT NULL,
    nbre_total_transaction          INTEGER         NOT NULL,
    montant_total_compensation      DECIMAL(18,2)   NOT NULL,
    indicateur_solde                VARCHAR(4)      NOT NULL,
    page_courante                   INTEGER         NOT NULL,
    derniere_page                   BOOLEAN         NOT NULL,
    transactions                    JSONB           NOT NULL,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_report_releve ON pi_transaction_report (identifiant_releve);
CREATE INDEX idx_txn_report_period ON pi_transaction_report (date_debut_compense, date_fin_compense);
