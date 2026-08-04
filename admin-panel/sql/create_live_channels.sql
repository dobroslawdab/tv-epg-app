-- Kanały live + token JWT sterowane zdalnie z Supabase.
-- Uruchom w Supabase SQL Editor (https://supabase.com/dashboard → SQL Editor).
--
-- CEL: podmiana URL-i kanałów ORAZ tokenu JWT bez rebuilda i bez releasu APK.
-- Aplikacja czyta te tabele przez anon key (patrz SupabaseLiveChannelsRepository.kt).
--
-- ⚠️ stream_url trzyma SZABLON z placeholderem {JWT}, nigdy gotowy URL z tokenem.
--    Token wstrzykiwany jest w aplikacji dopiero przy budowaniu MediaItem
--    (LiveMediaItemFactory), dzięki czemu rotacja tokenu działa na następnym
--    przełączeniu kanału — bez restartu aplikacji.

-- ============================================================
-- 1. Lista kanałów
-- ============================================================
CREATE TABLE IF NOT EXISTS public.live_channels (
    id                text PRIMARY KEY,                       -- np. 'tvp1'
    name              text NOT NULL,                          -- np. 'TVP1 HD'
    stream_url        text NOT NULL,                          -- SZABLON, może zawierać {JWT}
    logo_url          text,
    epg_id            text,                                   -- klucz dopasowania do EPG, np. 'TVP 1'
    category          text DEFAULT 'general',                 -- general | news | sports | music | kids
    is_geo_blocked    boolean NOT NULL DEFAULT false,
    is_available      boolean NOT NULL DEFAULT true,
    country           text NOT NULL DEFAULT 'PL',
    sort_order        integer NOT NULL DEFAULT 0,             -- kolejność = numeracja kanałów w apce
    supports_timeshift boolean NOT NULL DEFAULT true,         -- false = player wyłącza pauzę/przewijanie (okno live 36 s)
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_live_channels_sort_order ON public.live_channels(sort_order);
CREATE INDEX IF NOT EXISTS idx_live_channels_is_available ON public.live_channels(is_available);

ALTER TABLE public.live_channels ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "live_channels_public_read" ON public.live_channels;
CREATE POLICY "live_channels_public_read"
    ON public.live_channels FOR SELECT
    USING (true);

-- ============================================================
-- 2. Konfiguracja live: token JWT + tempo pollingu
-- ============================================================
CREATE TABLE IF NOT EXISTS public.live_config (
    id                          integer PRIMARY KEY DEFAULT 1,
    jwt                         text,                          -- AKTUALNY token do CDN
    poll_interval_seconds       integer NOT NULL DEFAULT 120,   -- co ile apka dopytuje o token (30–3600)
    channels_override_enabled   boolean NOT NULL DEFAULT true,  -- kill-switch: false = apka zostaje na asset JSON
    note                        text,                           -- notatka operacyjna, np. 'token wygasa 29.07 18:00'
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT live_config_single_row CHECK (id = 1)
);

ALTER TABLE public.live_config ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "live_config_public_read" ON public.live_config;
CREATE POLICY "live_config_public_read"
    ON public.live_config FOR SELECT
    USING (true);

-- Wiersz startowy — token pusty do momentu otrzymania go od zespołu CDN
INSERT INTO public.live_config (id, jwt, poll_interval_seconds, note)
VALUES (1, NULL, 120, 'Token nieustawiony — czekamy na JWT od zespolu CDN')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 3. Seed: 6 kanałów live Play (Redge Media / r.playcdn.tv)
--    Źródło: komentarz Bartłomieja Czechowskiego, 29/07/26
-- ============================================================
INSERT INTO public.live_channels
    (id, name, stream_url, logo_url, epg_id, category, is_geo_blocked, sort_order, supports_timeshift)
VALUES
    ('play_tvp1',   'TVP1',   'https://r.playcdn.tv/livedash/play/playtv/indigo/live/yVJZ2dq8bJ8/live.livx?jwt={JWT}', 'https://epg.ovh/logo/TVP1.pl.png',   'TVP 1',  'general', true, 1, false),
    ('play_tvp2',   'TVP2',   'https://r.playcdn.tv/livedash/play/playtv/indigo/live/x0XbrakDOZM/live.livx?jwt={JWT}', 'https://epg.ovh/logo/TVP2.pl.png',   'TVP 2',  'general', true, 2, false),
    ('play_tvn',    'TVN',    'https://r.playcdn.tv/livedash/play/playtv/indigo/live/wyWodZqb4aQ/live.livx?jwt={JWT}', 'https://epg.ovh/logo/TVN.pl.png',    'TVN',    'general', true, 3, false),
    ('play_tvn7',   'TVN7',   'https://r.playcdn.tv/livedash/play/playtv/indigo/live/XUbDR6RSD3Y/live.livx?jwt={JWT}', 'https://epg.ovh/logo/TVN7.pl.png',   'TVN 7',  'general', true, 4, false),
    ('play_polsat', 'Polsat', 'https://r.playcdn.tv/livedash/play/playtv/indigo/live/8MKHunTfwvg/live.livx?jwt={JWT}', 'https://epg.ovh/logo/Polsat.pl.png', 'Polsat', 'general', true, 5, false),
    ('play_tv4',    'TV4',    'https://r.playcdn.tv/livedash/play/playtv/indigo/live/ZpUBncvIyP4/live.livx?jwt={JWT}', 'https://epg.ovh/logo/TV4.pl.png',    'TV 4',   'general', true, 6, false)
ON CONFLICT (id) DO UPDATE SET
    stream_url = EXCLUDED.stream_url,
    name       = EXCLUDED.name,
    epg_id     = EXCLUDED.epg_id,
    updated_at = now();

-- ============================================================
-- 4. Operacje codzienne (do wklejania w SQL Editor)
-- ============================================================

-- >>> PODMIANA TOKENU (to jest cała operacja "bez rebuilda"):
-- UPDATE public.live_config
--    SET jwt = 'TU_WKLEJ_NOWY_TOKEN',
--        note = 'token z 03.08 14:00, wazny 2h',
--        updated_at = now()
--  WHERE id = 1;

-- >>> ZACIEŚNIENIE POLLINGU NA CZAS BADAŃ (np. co 30 s):
-- UPDATE public.live_config SET poll_interval_seconds = 30, updated_at = now() WHERE id = 1;

-- >>> POWRÓT DO KANAŁÓW Z ASSET JSON (kill-switch, gdy coś jest nie tak):
-- UPDATE public.live_config SET channels_override_enabled = false, updated_at = now() WHERE id = 1;

-- >>> PODMIANA URL-a JEDNEGO KANAŁU:
-- UPDATE public.live_channels
--    SET stream_url = 'https://.../live.livx?jwt={JWT}', updated_at = now()
--  WHERE id = 'play_tvp1';

-- >>> UKRYCIE KANAŁU BEZ USUWANIA:
-- UPDATE public.live_channels SET is_available = false WHERE id = 'play_tvn7';
