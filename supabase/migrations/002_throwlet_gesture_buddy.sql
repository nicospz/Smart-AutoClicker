-- Throwlet gesture and buddy-crop sync on the SAC Supabase project.
-- Requires 001_sac_sync.sql (uses sac_sync_profiles + sac_validate_profile).
--
-- Run after 001. One profile row covers scenarios, settings, catch needles, gestures, and buddy crops:
--   INSERT INTO sac_sync_profiles (profile_id, sync_secret)
--   VALUES ('your-profile-id', 'your-sync-secret')
--   ON CONFLICT (profile_id) DO UPDATE SET sync_secret = EXCLUDED.sync_secret;

-- Drop legacy Throwlet-only schema if present (separate throwlet_sync_profiles + SHA256 auth).
DROP FUNCTION IF EXISTS throwlet_upsert_buddy_crop(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, BOOLEAN, BIGINT, BIGINT
);
DROP FUNCTION IF EXISTS throwlet_list_buddy_crops(TEXT, TEXT);
DROP FUNCTION IF EXISTS throwlet_upsert_gesture(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, BIGINT, TEXT, TEXT, INTEGER, INTEGER, TEXT, BIGINT
);
DROP FUNCTION IF EXISTS throwlet_upsert_gesture(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, BIGINT, TEXT, TEXT, INTEGER, INTEGER, TEXT, INTEGER, BIGINT
);
DROP FUNCTION IF EXISTS throwlet_list_gestures(TEXT, TEXT);
DROP FUNCTION IF EXISTS throwlet_valid_sync_secret(TEXT, TEXT);

DROP TABLE IF EXISTS throwlet_buddy_crops;
DROP TABLE IF EXISTS throwlet_gestures;
DROP TABLE IF EXISTS throwlet_sync_profiles;

CREATE TABLE IF NOT EXISTS throwlet_gestures (
    profile_id TEXT NOT NULL REFERENCES sac_sync_profiles(profile_id) ON DELETE CASCADE,
    pokemon_key TEXT NOT NULL,
    pokemon_name TEXT NOT NULL,
    gesture_mode TEXT NOT NULL,
    payload_hex TEXT NOT NULL,
    event_count INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    helper_mode TEXT NOT NULL,
    source_lane TEXT NOT NULL,
    source_display_width INTEGER NOT NULL,
    source_display_height INTEGER NOT NULL,
    throw_score TEXT,
    updated_at_ms BIGINT NOT NULL,
    PRIMARY KEY (profile_id, pokemon_key, gesture_mode)
);

CREATE INDEX IF NOT EXISTS idx_throwlet_gestures_updated
    ON throwlet_gestures (profile_id, updated_at_ms DESC);

CREATE TABLE IF NOT EXISTS throwlet_buddy_crops (
    profile_id TEXT NOT NULL REFERENCES sac_sync_profiles(profile_id) ON DELETE CASCADE,
    pokemon_key TEXT NOT NULL,
    pokemon_name TEXT NOT NULL,
    image_png_base64 TEXT NOT NULL,
    source_lane TEXT NOT NULL,
    source_width INTEGER NOT NULL,
    source_height INTEGER NOT NULL,
    crop_left INTEGER NOT NULL,
    crop_top INTEGER NOT NULL,
    crop_right INTEGER NOT NULL,
    crop_bottom INTEGER NOT NULL,
    threshold_percent INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    PRIMARY KEY (profile_id, pokemon_key)
);

CREATE INDEX IF NOT EXISTS idx_throwlet_buddy_crops_updated
    ON throwlet_buddy_crops (profile_id, updated_at_ms DESC);

ALTER TABLE throwlet_gestures ENABLE ROW LEVEL SECURITY;
ALTER TABLE throwlet_buddy_crops ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE throwlet_gestures FROM anon, authenticated;
REVOKE ALL ON TABLE throwlet_buddy_crops FROM anon, authenticated;

CREATE OR REPLACE FUNCTION throwlet_list_gestures(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS TABLE (
    pokemon_key TEXT,
    pokemon_name TEXT,
    gesture_mode TEXT,
    payload_hex TEXT,
    event_count INTEGER,
    duration_ms BIGINT,
    helper_mode TEXT,
    source_lane TEXT,
    source_display_width INTEGER,
    source_display_height INTEGER,
    throw_score TEXT,
    updated_at_ms BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    RETURN QUERY
        SELECT
            g.pokemon_key,
            g.pokemon_name,
            g.gesture_mode,
            g.payload_hex,
            g.event_count,
            g.duration_ms,
            g.helper_mode,
            g.source_lane,
            g.source_display_width,
            g.source_display_height,
            g.throw_score,
            g.updated_at_ms
        FROM throwlet_gestures g
        WHERE g.profile_id = p_profile_id
        ORDER BY g.updated_at_ms DESC;
END;
$$;

CREATE OR REPLACE FUNCTION throwlet_upsert_gesture(
    p_profile_id TEXT,
    p_sync_secret TEXT,
    p_pokemon_key TEXT,
    p_pokemon_name TEXT,
    p_gesture_mode TEXT,
    p_payload_hex TEXT,
    p_event_count INTEGER,
    p_duration_ms BIGINT,
    p_helper_mode TEXT,
    p_source_lane TEXT,
    p_source_display_width INTEGER,
    p_source_display_height INTEGER,
    p_throw_score TEXT,
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
    INSERT INTO throwlet_gestures (
        profile_id,
        pokemon_key,
        pokemon_name,
        gesture_mode,
        payload_hex,
        event_count,
        duration_ms,
        helper_mode,
        source_lane,
        source_display_width,
        source_display_height,
        throw_score,
        updated_at_ms
    ) VALUES (
        p_profile_id,
        p_pokemon_key,
        p_pokemon_name,
        p_gesture_mode,
        p_payload_hex,
        p_event_count,
        p_duration_ms,
        p_helper_mode,
        p_source_lane,
        p_source_display_width,
        p_source_display_height,
        p_throw_score,
        p_updated_at_ms
    )
    ON CONFLICT (profile_id, pokemon_key, gesture_mode) DO UPDATE SET
        pokemon_name = EXCLUDED.pokemon_name,
        payload_hex = EXCLUDED.payload_hex,
        event_count = EXCLUDED.event_count,
        duration_ms = EXCLUDED.duration_ms,
        helper_mode = EXCLUDED.helper_mode,
        source_lane = EXCLUDED.source_lane,
        source_display_width = EXCLUDED.source_display_width,
        source_display_height = EXCLUDED.source_display_height,
        throw_score = EXCLUDED.throw_score,
        updated_at_ms = EXCLUDED.updated_at_ms
    WHERE throwlet_gestures.updated_at_ms < EXCLUDED.updated_at_ms;
END;
$$;

CREATE OR REPLACE FUNCTION throwlet_list_buddy_crops(p_profile_id TEXT, p_sync_secret TEXT)
RETURNS TABLE (
    pokemon_key TEXT,
    pokemon_name TEXT,
    image_png_base64 TEXT,
    source_lane TEXT,
    source_width INTEGER,
    source_height INTEGER,
    crop_left INTEGER,
    crop_top INTEGER,
    crop_right INTEGER,
    crop_bottom INTEGER,
    threshold_percent INTEGER,
    enabled BOOLEAN,
    created_at_ms BIGINT,
    updated_at_ms BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT sac_validate_profile(p_profile_id, p_sync_secret) THEN
        RAISE EXCEPTION 'invalid profile';
    END IF;
    RETURN QUERY
        SELECT
            c.pokemon_key,
            c.pokemon_name,
            c.image_png_base64,
            c.source_lane,
            c.source_width,
            c.source_height,
            c.crop_left,
            c.crop_top,
            c.crop_right,
            c.crop_bottom,
            c.threshold_percent,
            c.enabled,
            c.created_at_ms,
            c.updated_at_ms
        FROM throwlet_buddy_crops c
        WHERE c.profile_id = p_profile_id
        ORDER BY c.updated_at_ms DESC;
END;
$$;

CREATE OR REPLACE FUNCTION throwlet_upsert_buddy_crop(
    p_profile_id TEXT,
    p_sync_secret TEXT,
    p_pokemon_key TEXT,
    p_pokemon_name TEXT,
    p_image_png_base64 TEXT,
    p_source_lane TEXT,
    p_source_width INTEGER,
    p_source_height INTEGER,
    p_crop_left INTEGER,
    p_crop_top INTEGER,
    p_crop_right INTEGER,
    p_crop_bottom INTEGER,
    p_threshold_percent INTEGER,
    p_enabled BOOLEAN,
    p_created_at_ms BIGINT,
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
    INSERT INTO throwlet_buddy_crops (
        profile_id,
        pokemon_key,
        pokemon_name,
        image_png_base64,
        source_lane,
        source_width,
        source_height,
        crop_left,
        crop_top,
        crop_right,
        crop_bottom,
        threshold_percent,
        enabled,
        created_at_ms,
        updated_at_ms
    ) VALUES (
        p_profile_id,
        p_pokemon_key,
        p_pokemon_name,
        p_image_png_base64,
        p_source_lane,
        p_source_width,
        p_source_height,
        p_crop_left,
        p_crop_top,
        p_crop_right,
        p_crop_bottom,
        p_threshold_percent,
        p_enabled,
        p_created_at_ms,
        p_updated_at_ms
    )
    ON CONFLICT (profile_id, pokemon_key) DO UPDATE SET
        pokemon_name = EXCLUDED.pokemon_name,
        image_png_base64 = EXCLUDED.image_png_base64,
        source_lane = EXCLUDED.source_lane,
        source_width = EXCLUDED.source_width,
        source_height = EXCLUDED.source_height,
        crop_left = EXCLUDED.crop_left,
        crop_top = EXCLUDED.crop_top,
        crop_right = EXCLUDED.crop_right,
        crop_bottom = EXCLUDED.crop_bottom,
        threshold_percent = EXCLUDED.threshold_percent,
        enabled = EXCLUDED.enabled,
        created_at_ms = EXCLUDED.created_at_ms,
        updated_at_ms = EXCLUDED.updated_at_ms
    WHERE throwlet_buddy_crops.updated_at_ms < EXCLUDED.updated_at_ms;
END;
$$;

GRANT EXECUTE ON FUNCTION throwlet_list_gestures(TEXT, TEXT) TO anon;
GRANT EXECUTE ON FUNCTION throwlet_upsert_gesture(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, BIGINT, TEXT, TEXT, INTEGER, INTEGER, TEXT, BIGINT
) TO anon;
GRANT EXECUTE ON FUNCTION throwlet_list_buddy_crops(TEXT, TEXT) TO anon;
GRANT EXECUTE ON FUNCTION throwlet_upsert_buddy_crop(
    TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, INTEGER, BOOLEAN, BIGINT, BIGINT
) TO anon;

NOTIFY pgrst, 'reload schema';
