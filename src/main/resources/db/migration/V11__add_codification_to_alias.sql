ALTER TABLE pi_alias ADD COLUMN codification VARCHAR(30);

WITH ranked AS (
    SELECT id,
           code_membre_participant
               || '-'
               || CASE type_alias
                      WHEN 'MBNO' THEN 'MB'
                      WHEN 'SHID' THEN 'SH'
                      WHEN 'MCOD' THEN 'MC'
                      ELSE 'XX'
                  END
               || '-'
               || TO_CHAR(created_at, 'YYYYMMDD')
               || '-'
               || LPAD(CAST(
                      ROW_NUMBER() OVER (
                          PARTITION BY code_membre_participant, DATE(created_at)
                          ORDER BY id
                      ) AS TEXT
                  ), 5, '0') AS new_codification
    FROM pi_alias
    WHERE codification IS NULL
)
UPDATE pi_alias
SET codification = ranked.new_codification
FROM ranked
WHERE pi_alias.id = ranked.id;

CREATE UNIQUE INDEX uq_pi_alias_codification ON pi_alias (codification) WHERE codification IS NOT NULL;
