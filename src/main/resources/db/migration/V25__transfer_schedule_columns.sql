-- Phase 3.4a: schedule metadata on pi_transfer for the mobile two-phase flow's
-- send_schedule action (Programme + Abonnement per the BCEAO remote spec
-- TransactionProgramme / TransactionAbonnement schemas).
--
-- Storage model: a PiTransfer row with action='send_schedule' holds the
-- schedule recipe (date_debut, date_fin, frequence, periodicite); each
-- execution created by the scheduler runner inserts a new PiTransfer row with
-- parent_schedule_id pointing back to the recipe. This keeps a single table
-- for both schedules and their spawned executions and matches the mobile spec
-- where a single Transaction object can carry either shape.
--
-- ONE-OFF DEFERRED (Programme): frequence = NULL, periodicite = NULL,
--   date_debut set, date_fin = NULL.
-- RECURRING (Abonnement): frequence in ('J','S','M','A'), periodicite ≥ 1,
--   date_debut set, date_fin optional (open-ended if NULL).

ALTER TABLE pi_transfer
    ADD COLUMN action                   VARCHAR(15),
    ADD COLUMN date_debut               TIMESTAMP,
    ADD COLUMN date_fin                 TIMESTAMP,
    ADD COLUMN frequence                VARCHAR(1),
    ADD COLUMN periodicite              INTEGER,
    ADD COLUMN next_execution_date      DATE,
    ADD COLUMN parent_schedule_id       BIGINT,
    ADD COLUMN active                   BOOLEAN DEFAULT TRUE,
    ADD COLUMN subscription_id          VARCHAR(50);

-- Self-referencing FK so a child execution always points at a valid parent.
ALTER TABLE pi_transfer
    ADD CONSTRAINT fk_pi_transfer_parent_schedule
    FOREIGN KEY (parent_schedule_id) REFERENCES pi_transfer(id)
    ON DELETE SET NULL;

-- Index for the scheduler runner's due-lookup (Phase 3.4b). Only schedule
-- parent rows need to be scanned, so the WHERE clause keeps the index small.
CREATE INDEX idx_pi_transfer_schedule_due
    ON pi_transfer (next_execution_date, active)
    WHERE action = 'SEND_SCHEDULE';
