-- Altera a tabela stats para usar user_id como PK (suporte a @MapsId)
ALTER TABLE stats DROP CONSTRAINT stats_pkey;
ALTER TABLE stats DROP COLUMN id;
ALTER TABLE stats ADD PRIMARY KEY (user_id);
