-- Smart Auto Clicker full-device sync schema.
-- Auth: profile_id + sync_secret validated in each RPC (personal multi-device).
--
-- Device-local (NOT synced): split-screen calibration (per-device touch/screenshot offsets).
--
-- After running 001 and 002_throwlet_gesture_buddy.sql, register one profile for all sync
-- (scenarios, settings, catch needles, gestures, buddy crops). Match local.properties creds:
--   INSERT INTO sac_sync_profiles (profile_id, sync_secret)
--   VALUES ('your-profile-id', 'your-sync-secret')
--   ON CONFLICT (profile_id) DO UPDATE SET sync_secret = EXCLUDED.sync_secret;

CREATE TABLE IF NOT EXISTS sac_sync_profiles (
    profile_id TEXT PRIMARY KEY,
    sync_secret TEXT NOT NULL,
    created_at_ms BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE TABLE IF NOT EXISTS sac_scenarios (
    profile_id TEXT NOT NULL REFERENCES sac_sync_profiles(profile_id) ON DELETE CASCADE,
    sync_id TEXT NOT NULL,
    scenario_type TEXT NOT NULL CHECK (scenario_type IN ('smart', 'dumb')),
    payload_json JSONB NOT NULL,
    schema_version INTEGER NOT NULL,
    screen_width INTEGER NOT NULL DEFAULT 0,
    screen_height INTEGER NOT NULL DEFAULT 0,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT,
    PRIMARY KEY (profile_id, sync_id)
);

CREATE INDEX IF NOT EXISTS idx_sac_scenarios_updated ON sac_scenarios (profile_id, updated_at_ms DESC);

CREATE TABLE IF NOT EXISTS sac_condition_assets (
    profile_id TEXT NOT NULL REFERENCES sac_sync_profiles(profile_id) ON DELETE CASCADE,
    content_hash TEXT NOT NULL,
    image_png_base64 TEXT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    PRIMARY KEY (profile_id, content_hash)
);

CREATE TABLE IF NOT EXISTS sac_profile_settings (
    profile_id TEXT PRIMARY KEY REFERENCES sac_sync_profiles(profile_id) ON DELETE CASCADE,
    settings_json JSONB NOT NULL,
    updated_at_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sac_throwlet_catch_needles (
    profile_id TEXT NOT NULL REFERENCES sac_sync_profiles(profile_id) ON DELETE CASCADE,
    needle_id TEXT NOT NULL,
    feature TEXT NOT NULL,
    lane TEXT NOT NULL,
    variant_order INTEGER NOT NULL DEFAULT 0,
    image_png_base64 TEXT NOT NULL,
    source_width INTEGER NOT NULL,
    source_height INTEGER NOT NULL,
    crop_left INTEGER NOT NULL,
    crop_top INTEGER NOT NULL,
    crop_right INTEGER NOT NULL,
    crop_bottom INTEGER NOT NULL,
    search_left INTEGER NOT NULL,
    search_top INTEGER NOT NULL,
    search_right INTEGER NOT NULL,
    search_bottom INTEGER NOT NULL,
    threshold INTEGER NOT NULL,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    deleted_at_ms BIGINT,
    PRIMARY KEY (profile_id, needle_id)
);

CREATE OR REPLACE FUNCTION sac_validate_profile(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM sac_sync_profiles
        WHERE profile_id = p_profile_id AND sync_secret = p_sync_secret
    );
END;
$$;

CREATE OR REPLACE FUNCTION sac_list_scenarios(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS SETOF sac_scenarios
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    RETURN QUERY
        SELECT * FROM sac_scenarios WHERE profile_id = p_profile_id;
END;
$$;

CREATE OR REPLACE FUNCTION sac_upsert_scenario(
    p_profile_id TEXT,
    p_sync_secret TEXT,
    p_sync_id TEXT,
    p_scenario_type TEXT,
    p_payload_json JSONB,
    p_schema_version INTEGER,
    p_screen_width INTEGER,
    p_screen_height INTEGER,
    p_updated_at_ms BIGINT,
    p_deleted_at_ms BIGINT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    INSERT INTO sac_scenarios (
        profile_id, sync_id, scenario_type, payload_json, schema_version,
        screen_width, screen_height, updated_at_ms, deleted_at_ms
    ) VALUES (
        p_profile_id, p_sync_id, p_scenario_type, p_payload_json, p_schema_version,
        p_screen_width, p_screen_height, p_updated_at_ms, p_deleted_at_ms
    )
    ON CONFLICT (profile_id, sync_id) DO UPDATE SET
        scenario_type = EXCLUDED.scenario_type,
        payload_json = EXCLUDED.payload_json,
        schema_version = EXCLUDED.schema_version,
        screen_width = EXCLUDED.screen_width,
        screen_height = EXCLUDED.screen_height,
        updated_at_ms = EXCLUDED.updated_at_ms,
        deleted_at_ms = EXCLUDED.deleted_at_ms
    WHERE sac_scenarios.updated_at_ms <= EXCLUDED.updated_at_ms;
END;
$$;

CREATE OR REPLACE FUNCTION sac_list_condition_assets(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS SETOF sac_condition_assets
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    RETURN QUERY SELECT * FROM sac_condition_assets WHERE profile_id = p_profile_id;
END;
$$;

CREATE OR REPLACE FUNCTION sac_upsert_condition_asset(
    p_profile_id TEXT,
    p_sync_secret TEXT,
    p_content_hash TEXT,
    p_image_png_base64 TEXT,
    p_updated_at_ms BIGINT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    INSERT INTO sac_condition_assets (profile_id, content_hash, image_png_base64, updated_at_ms)
    VALUES (p_profile_id, p_content_hash, p_image_png_base64, p_updated_at_ms)
    ON CONFLICT (profile_id, content_hash) DO UPDATE SET
        image_png_base64 = EXCLUDED.image_png_base64,
        updated_at_ms = EXCLUDED.updated_at_ms
    WHERE sac_condition_assets.updated_at_ms <= EXCLUDED.updated_at_ms;
END;
$$;

CREATE OR REPLACE FUNCTION sac_get_profile_settings(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS sac_profile_settings
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    result sac_profile_settings;
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    SELECT * INTO result FROM sac_profile_settings WHERE profile_id = p_profile_id;
    RETURN result;
END;
$$;

CREATE OR REPLACE FUNCTION sac_upsert_profile_settings(
    p_profile_id TEXT,
    p_sync_secret TEXT,
    p_settings_json JSONB,
    p_updated_at_ms BIGINT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    INSERT INTO sac_profile_settings (profile_id, settings_json, updated_at_ms)
    VALUES (p_profile_id, p_settings_json, p_updated_at_ms)
    ON CONFLICT (profile_id) DO UPDATE SET
        settings_json = EXCLUDED.settings_json,
        updated_at_ms = EXCLUDED.updated_at_ms
    WHERE sac_profile_settings.updated_at_ms <= EXCLUDED.updated_at_ms;
END;
$$;

CREATE OR REPLACE FUNCTION sac_list_catch_needles(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS SETOF sac_throwlet_catch_needles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    RETURN QUERY SELECT * FROM sac_throwlet_catch_needles WHERE profile_id = p_profile_id;
END;
$$;

CREATE OR REPLACE FUNCTION sac_upsert_catch_needle(
    p_profile_id TEXT,
    p_sync_secret TEXT,
    p_needle_id TEXT,
    p_feature TEXT,
    p_lane TEXT,
    p_variant_order INTEGER,
    p_image_png_base64 TEXT,
    p_source_width INTEGER,
    p_source_height INTEGER,
    p_crop_left INTEGER,
    p_crop_top INTEGER,
    p_crop_right INTEGER,
    p_crop_bottom INTEGER,
    p_search_left INTEGER,
    p_search_top INTEGER,
    p_search_right INTEGER,
    p_search_bottom INTEGER,
    p_threshold INTEGER,
    p_created_at_ms BIGINT,
    p_updated_at_ms BIGINT,
    p_deleted_at_ms BIGINT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    INSERT INTO sac_throwlet_catch_needles (
        profile_id, needle_id, feature, lane, variant_order, image_png_base64,
        source_width, source_height,
        crop_left, crop_top, crop_right, crop_bottom,
        search_left, search_top, search_right, search_bottom,
        threshold, created_at_ms, updated_at_ms, deleted_at_ms
    ) VALUES (
        p_profile_id, p_needle_id, p_feature, p_lane, p_variant_order, p_image_png_base64,
        p_source_width, p_source_height,
        p_crop_left, p_crop_top, p_crop_right, p_crop_bottom,
        p_search_left, p_search_top, p_search_right, p_search_bottom,
        p_threshold, p_created_at_ms, p_updated_at_ms, p_deleted_at_ms
    )
    ON CONFLICT (profile_id, needle_id) DO UPDATE SET
        feature = EXCLUDED.feature,
        lane = EXCLUDED.lane,
        variant_order = EXCLUDED.variant_order,
        image_png_base64 = EXCLUDED.image_png_base64,
        source_width = EXCLUDED.source_width,
        source_height = EXCLUDED.source_height,
        crop_left = EXCLUDED.crop_left,
        crop_top = EXCLUDED.crop_top,
        crop_right = EXCLUDED.crop_right,
        crop_bottom = EXCLUDED.crop_bottom,
        search_left = EXCLUDED.search_left,
        search_top = EXCLUDED.search_top,
        search_right = EXCLUDED.search_right,
        search_bottom = EXCLUDED.search_bottom,
        threshold = EXCLUDED.threshold,
        updated_at_ms = EXCLUDED.updated_at_ms,
        deleted_at_ms = EXCLUDED.deleted_at_ms
    WHERE sac_throwlet_catch_needles.updated_at_ms <= EXCLUDED.updated_at_ms;
END;
$$;
