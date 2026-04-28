-- Élargissement des colonnes {@code code_raison} sur pi_transfer (V1, varchar(10)),
-- pi_request_to_pay (V19, varchar(10)) et pi_identity_verification (V18, varchar(4)).
--
-- Le pattern BCEAO canonique pour codeRaison est {@code [A-Z]{2}\d{2}} (4 chars,
-- ex: AC01, BE01, MS03), d'où les longueurs serrées initiales. Mais l'AIP renvoie
-- aussi en pratique des codes longs et libres dans certains ADMI.002 :
--   - TransactionNotFound (18 chars)
--   - InsufficientFunds (17 chars)
--   - InvalidAccount (14 chars)
-- L'insertion de ces valeurs déclenche {@code SQLState 22001 — value too long
-- for type character varying(10)} et empêche la transition de statut.
--
-- 50 chars laisse une marge confortable pour les variantes futures sans tomber
-- dans le sur-dimensionnement (text/varchar illimité).

ALTER TABLE pi_transfer ALTER COLUMN code_raison TYPE VARCHAR(50);
ALTER TABLE pi_request_to_pay ALTER COLUMN code_raison TYPE VARCHAR(50);
ALTER TABLE pi_identity_verification ALTER COLUMN code_raison TYPE VARCHAR(50);
