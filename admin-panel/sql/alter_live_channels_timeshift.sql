-- Migracja: flaga supports_timeshift dla kanałów live.
-- Uruchom w Supabase SQL Editor (tabele live_channels/live_config już istnieją).
--
-- Kanały Play (CDN Redge) mają timeShiftBufferDepth=PT36S — okno live 36 sekund.
-- Pauza/przewijanie wyrzuciłyby playback poza playlistę (BEHIND_LIVE_WINDOW),
-- więc player wyłącza timeshift dla kanałów z supports_timeshift=false.

ALTER TABLE public.live_channels
    ADD COLUMN IF NOT EXISTS supports_timeshift boolean NOT NULL DEFAULT true;

-- Wszystkie 6 kanałów Play: bez pauzy/przewijania
UPDATE public.live_channels
   SET supports_timeshift = false,
       updated_at = now()
 WHERE id LIKE 'play_%';

-- Weryfikacja
SELECT id, name, supports_timeshift FROM public.live_channels ORDER BY sort_order;
