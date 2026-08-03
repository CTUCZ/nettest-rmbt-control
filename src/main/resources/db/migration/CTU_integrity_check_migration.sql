-- Play Integrity: per-request verdict/monitoring records for POST /testRequest.
-- Applied manually (Flyway is disabled), independent of CTU_DB_migration.sql.
CREATE TABLE public.test_integrity (
    uid                   bigserial PRIMARY KEY,
    created_date          timestamptz NOT NULL,
    modified_date         timestamptz NOT NULL,
    test_uid              int8 NULL REFERENCES public.test(uid),  -- NULL = rejected, or allowed where no test was created; the authoritative field is "action"
    client_uuid           uuid NULL,                              -- support lookup key
    platform              varchar(20) NULL,                       -- self-reported client platform
    provider              varchar(30) NOT NULL,                   -- PLAY_INTEGRITY / NONE
    status                varchar(20) NOT NULL,                   -- PASS / FAIL / CLIENT_ERROR / UNAVAILABLE / MISSING
    action                varchar(10) NOT NULL,                   -- ALLOWED / REJECTED
    failed_checks         jsonb NULL,                             -- which verdict checks failed
    client_error          varchar(20) NULL,                       -- integrity_error value from the request
    client_error_detail   varchar(200) NULL,
    software_version_code int4 NULL,                              -- not stored on the test table; needed for per-version stats
    token_digest          varchar(64) NULL,                       -- SHA-256 of the token
    decode_latency_ms     int4 NULL
);

CREATE INDEX test_integrity_test_uid_idx    ON public.test_integrity (test_uid);
CREATE INDEX test_integrity_client_uuid_idx ON public.test_integrity (client_uuid);
-- Partial unique index = atomic first-seen-wins anti-replay across server instances.
-- Deliberately NO freshness window here: a digest stays blocked until the retention cleanup
-- removes the row (a legitimate client never reuses a token, so a longer block is harmless).
CREATE UNIQUE INDEX test_integrity_token_digest_uq ON public.test_integrity (token_digest)
    WHERE token_digest IS NOT NULL;

-- When this script is applied by a role other than the application's DB user, the application
-- role (default rmbt_control - adjust to the environment) additionally needs:
--   GRANT SELECT, INSERT, UPDATE ON public.test_integrity TO rmbt_control;
--   GRANT USAGE, SELECT ON SEQUENCE public.test_integrity_uid_seq TO rmbt_control;
-- Without the sequence grant every insert fails with "permission denied for sequence
-- test_integrity_uid_seq" (the check then fail-opens, so measurements still run, but no
-- integrity data is recorded).
