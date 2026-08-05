-- ============================================================
-- PODMIANA TOKENU JWT KANAŁÓW LIVE (codzienna operacja)
-- ============================================================
-- Wklej token w miejsce TU_WKLEJ_TOKEN i uruchom w Supabase SQL Editor.
-- Urządzenia złapią go w ≤ poll_interval_seconds (domyślnie 120 s),
-- a natychmiast przy przełączeniu kanału.

UPDATE public.live_config
   SET jwt = 'TU_WKLEJ_TOKEN',
       note = 'token z ' || to_char(now(), 'DD.MM HH24:MI'),
       updated_at = now()
 WHERE id = 1;

-- Weryfikacja (bez pokazywania całego tokenu)
SELECT id,
       length(jwt)                          AS dlugosc_tokenu,
       left(jwt, 12) || '…'                 AS prefiks,
       poll_interval_seconds,
       note,
       updated_at
  FROM public.live_config
 WHERE id = 1;


-- ============================================================
-- OPCJONALNIE — pozwól aktualizować token BEZ dashboardu
-- ============================================================
-- Domyślnie RLS na live_config dopuszcza tylko SELECT dla anon, więc token można
-- zmienić wyłącznie z dashboardu (service_role). Poniższa polityka pozwala
-- aktualizować wiersz anon keyem — wtedy podmiana tokenu to jeden curl z terminala
-- (albo ./scripts/set_live_token.sh).
--
-- ⚠️ ŚWIADOME USTĘPSTWO: anon key jest w APK, więc każdy z APK-iem może nadpisać
-- token. Dla makiety badawczej akceptowalne (token i tak jest krótkożyciowy
-- i publiczny wśród testerów). NIE stosować w produkcie.
--
-- Odkomentuj i uruchom, jeśli chcesz tę wygodę:

-- DROP POLICY IF EXISTS "live_config_anon_update" ON public.live_config;
-- CREATE POLICY "live_config_anon_update"
--     ON public.live_config FOR UPDATE
--     USING (id = 1)
--     WITH CHECK (id = 1);

-- Cofnięcie (powrót do read-only dla anon):
-- DROP POLICY IF EXISTS "live_config_anon_update" ON public.live_config;
