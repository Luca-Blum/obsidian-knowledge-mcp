-- One line per Collection; the postgres-init service runs this after isolate-collection.sql, in the same session.
-- Each password comes from .env through a psql variable.
CALL pg_temp.isolate_collection('engineering', :'engineering_password');
