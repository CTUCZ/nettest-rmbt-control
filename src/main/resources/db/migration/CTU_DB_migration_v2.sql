-- 2026: Backend and Android client updates 
-- New table: test_server_quality
CREATE SEQUENCE IF NOT EXISTS public.test_server_quality_uid_seq INCREMENT BY 1 START WITH 1;
CREATE TABLE IF NOT EXISTS public.test_server_quality (
    uid         int8        NOT null,
    server_uuid uuid        NOT NULL,
    "timestamp" timestamptz NOT NULL,
    protocol    int4        NOT NULL,
    reachable   bool        NOT NULL,
    latency_ms  float8      NULL,
    CONSTRAINT test_server_quality_pkey PRIMARY KEY (uid),
    CONSTRAINT test_server_quality_server_uuid_fkey FOREIGN KEY (server_uuid) REFERENCES public.test_server(uuid)
);

CREATE INDEX ix_test_server_quality_server_uuid ON public.test_server_quality USING btree (server_uuid);
CREATE INDEX ix_test_server_quality_timestamp ON public.test_server_quality USING btree ("timestamp");
GRANT INSERT, UPDATE ON TABLE public.test_server_quality TO rmbt_group_control;
GRANT SELECT ON TABLE public.test_server_quality TO rmbt_group_read_only;

-- New table: fences
CREATE TABLE IF NOT EXISTS public.fences (
    uid            serial4               NOT NULL,                 -- @Id @GeneratedValue(IDENTITY)
    open_test_uuid uuid                  NULL,
    fence_id       int4                  NULL,                     -- fence number, starts at 1
    technology_id  int4                  NULL,
    avg_ping_ms    float8                NULL,
    technology     varchar(50)           NULL,
    offset_ms      int4                  NULL,                     -- can be negative
    duration_ms    int4                  NULL,
    radius         float8                NULL,                     -- meters
    fence_time     timestamptz           NULL,
    geom4326       geometry(Point, 4326) NULL,                     -- PostGIS point, WGS84
    signal         float8                NULL,                     -- min RSRP dBm
    accuracy       float8                NULL,
    provider       varchar(50)           NULL,
    altitude       float8                NULL,
    bearing        float8                NULL,
    speed          float8                NULL,
    CONSTRAINT fences_open_test_uuid_fence_id_idx UNIQUE (open_test_uuid, fence_id),
    CONSTRAINT fences_pkey PRIMARY KEY (uid),
    CONSTRAINT fences_open_test_uuid_fkey FOREIGN KEY (open_test_uuid) REFERENCES public.test(open_test_uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS fences_open_test_uuid_idx ON public.fences (open_test_uuid);
CREATE INDEX IF NOT EXISTS fences_geom4326_gix       ON public.fences USING gist (geom4326);
ALTER TABLE public.test ADD termination_cause varchar(100) NULL;

-- Test server quality sequences
ALTER SEQUENCE public.test_server_quality_uid_seq OWNED BY public.test_server_quality.uid;
GRANT SELECT, UPDATE ON SEQUENCE public.test_server_quality_uid_seq TO rmbt_group_control;
GRANT SELECT ON SEQUENCE public.test_server_quality_uid_seq TO rmbt_group_read_only;

-- New columns in test table
ALTER TABLE public.test ADD COLUMN IF NOT EXISTS apn         varchar(100) NULL;
ALTER TABLE public.test ADD COLUMN IF NOT EXISTS nat_type_v4 varchar(200) NULL;
ALTER TABLE public.test ADD COLUMN IF NOT EXISTS nat_type_v6 varchar(200) NULL;

-- Drop column in test_server table
ALTER TABLE public.test_server DROP COLUMN IF EXISTS web_address;

-- Settings and test duration
UPDATE public.settings
	SET "key"='rmbt_duration_seconds'
	WHERE uid= (select uid from public.settings where key = 'rmbt_duration');
