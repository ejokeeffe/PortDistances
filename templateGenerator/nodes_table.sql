-- DROP TABLE sp_nodes_5deg;

CREATE TABLE sp_nodes_2deg
(
  id integer NOT NULL,
  x double precision,
  y double precision,
  longitude double precision,
  latitude double precision,
  label character varying(255) DEFAULT ''::character varying,
  CONSTRAINT primkey_sp_nodes_2deg PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE sp_nodes_2deg
  OWNER TO gephi;
