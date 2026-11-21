CREATE TABLE IF NOT EXISTS region (id integer PRIMARY KEY, name varchar(50));
INSERT INTO region (id, name) VALUES (1, 'Región de Ñuble') ON CONFLICT (id) DO NOTHING;
