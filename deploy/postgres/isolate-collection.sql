-- Gives a Collection its own login role, named like the Collection, which owns the Collection's schema and cannot see
-- any other. The postgres-init service runs this as the superuser on every deploy, so every step may run again.
-- The procedure is temporary and vanishes with the session: no Collection role can call it.

-- A Collection role may not create extensions, so the index's migration finds pgvector already there
CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- Postgres 15 and later already behave like this; stated here so isolation does not hang on the version.
-- Collection roles still use public for pgvector's type, but create nothing outside their own schema.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
DO $$ BEGIN
    EXECUTE format('REVOKE CREATE, TEMPORARY ON DATABASE %I FROM PUBLIC', current_database());
END $$;

CREATE OR REPLACE PROCEDURE pg_temp.isolate_collection(collection name, password text)
LANGUAGE plpgsql AS $$
DECLARE
    relation record;
    routine record;
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = collection) THEN
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', collection, password);
    ELSE
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', collection, password);
    END IF;

    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION %I', collection, collection);
    EXECUTE format('ALTER SCHEMA %I OWNER TO %I', collection, collection);
    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM PUBLIC', collection);

    -- A schema built before its role existed: hand over the index in it
    FOR relation IN
        SELECT c.relname, c.relkind
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = collection AND c.relkind IN ('r', 'p', 'v', 'm')
    LOOP
        EXECUTE format('ALTER %s %I.%I OWNER TO %I',
            CASE relation.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
            collection, relation.relname, collection);
    END LOOP;
    FOR routine IN
        SELECT p.oid::regprocedure AS signature
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = collection
    LOOP
        EXECUTE format('ALTER ROUTINE %s OWNER TO %I', routine.signature, collection);
    END LOOP;
END
$$;
