-- Tabela banerów hero slidera dla zakładki APLIKACJE
-- Schemat 1:1 z odkrywaj_slider, tylko inna nazwa.
-- Uruchom w Supabase SQL Editor (https://supabase.com/dashboard → SQL Editor).

CREATE TABLE IF NOT EXISTS public.aplikacje_slider (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_order          integer NOT NULL,
    source_type         text NOT NULL DEFAULT 'manual',         -- 'manual' | 'movie' | 'vod'
    source_movie_id     bigint,
    source_vod_id       text,
    title               text NOT NULL DEFAULT '',
    title_max_lines     integer NOT NULL DEFAULT 2,
    description         text NOT NULL DEFAULT '',
    show_metadata       boolean NOT NULL DEFAULT false,
    metadata_text       text NOT NULL DEFAULT '',
    backdrop_url        text NOT NULL DEFAULT '',               -- ilustracja banera (1920x1080)
    logo_url            text NOT NULL DEFAULT '',               -- logo aplikacji (np. HBO Max, Netflix)
    button_type         text NOT NULL DEFAULT 'custom',         -- 'custom' = button_custom_text
    button_custom_text  text NOT NULL DEFAULT 'Otwórz aplikację', -- np. "Otwórz aplikację" / "Zainstaluj aplikację"
    youtube_url         text NOT NULL DEFAULT '',
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_aplikacje_slider_slot_order ON public.aplikacje_slider(slot_order);
CREATE INDEX IF NOT EXISTS idx_aplikacje_slider_is_active ON public.aplikacje_slider(is_active);

-- RLS: read public, write tylko service_role/authenticated
ALTER TABLE public.aplikacje_slider ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "aplikacje_slider read public" ON public.aplikacje_slider;
CREATE POLICY "aplikacje_slider read public"
    ON public.aplikacje_slider
    FOR SELECT
    TO anon, authenticated
    USING (true);

DROP POLICY IF EXISTS "aplikacje_slider write authenticated" ON public.aplikacje_slider;
CREATE POLICY "aplikacje_slider write authenticated"
    ON public.aplikacje_slider
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

-- Storage bucket dla ilustracji banerów (public read)
-- Uruchom osobno w Supabase Dashboard → Storage → New bucket → name: "aplikacje-slider", Public.
-- Lub w SQL:
INSERT INTO storage.buckets (id, name, public)
VALUES ('aplikacje-slider', 'aplikacje-slider', true)
ON CONFLICT (id) DO UPDATE SET public = EXCLUDED.public;

-- Storage policies (analogicznie do odkrywaj-slider)
DROP POLICY IF EXISTS "aplikacje-slider read public" ON storage.objects;
CREATE POLICY "aplikacje-slider read public"
    ON storage.objects
    FOR SELECT
    TO anon, authenticated
    USING (bucket_id = 'aplikacje-slider');

DROP POLICY IF EXISTS "aplikacje-slider write authenticated" ON storage.objects;
CREATE POLICY "aplikacje-slider write authenticated"
    ON storage.objects
    FOR ALL
    TO authenticated
    USING (bucket_id = 'aplikacje-slider')
    WITH CHECK (bucket_id = 'aplikacje-slider');
