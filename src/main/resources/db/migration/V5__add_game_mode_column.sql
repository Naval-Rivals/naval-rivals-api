-- Adiciona coluna game_mode nas tabelas rooms e game_results

ALTER TABLE rooms
    ADD COLUMN game_mode VARCHAR(10) NOT NULL DEFAULT 'CLASSIC';

ALTER TABLE game_results
    ADD COLUMN game_mode VARCHAR(10) NOT NULL DEFAULT 'CLASSIC';
