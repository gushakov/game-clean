-- Bootstraps a read-only PostgreSQL user for Claude MCP access.
-- Mounted into /docker-entrypoint-initdb.d/ via docker-compose volume.
-- Runs automatically on first container creation (initdb).
--
-- The read-only role lets the postgres MCP introspect the schema (and the
-- Flyway migration history) without any write or business-data access. The
-- global PreToolUse hook further restricts MCP SQL to schema-only SELECTs.

CREATE ROLE claude_readonly LOGIN PASSWORD 'readonly';
GRANT CONNECT ON DATABASE gameclean TO claude_readonly;
GRANT USAGE ON SCHEMA public TO claude_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO claude_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO claude_readonly;
