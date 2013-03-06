-- Table: sp_edges_2deg

-- DROP TABLE sp_edges_2deg;

CREATE TABLE sp_edges_2deg
(
  id integer NOT NULL DEFAULT nextval('sp_edges_2deg_seq_id'::regclass),
  source integer,
  target integer,
  weight double precision,
  CONSTRAINT primkey_sp_edges_2deg PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE sp_edges_2deg
  OWNER TO gephi;