-- ============================================================
-- PRZYWRÓCENIE: kanały live Z PRZEWIJANIEM (stara lista sprzed tokenów Play)
-- ============================================================
--
-- Stara 9-kanałowa lista (TVP1 HD orange.pl, Polsat pluscdn, 4Fun, TVN24 itd.)
-- z działającą pauzą/przewijaniem NIE została nigdzie skasowana — siedzi w APK
-- jako assets/tv_channels_with_streams.json i jest fallbackiem ChannelManagera.
--
-- Żeby apka wróciła na nią, wystarczy wyłączyć nadpisywanie z Supabase:

UPDATE public.live_config
   SET channels_override_enabled = false,
       note = 'kill-switch: apka gra ze starej listy asset JSON (z przewijaniem)',
       updated_at = now()
 WHERE id = 1;

-- Urządzenia przełączą się przy następnym starcie aplikacji
-- (lista kanałów jest czytana przy initialize; token polling tego nie podmienia).

-- ============================================================
-- POWRÓT na 6 kanałów Play z tokenami:
-- ============================================================
-- UPDATE public.live_config
--    SET channels_override_enabled = true,
--        note = 'kanaly Play z tokenem JWT',
--        updated_at = now()
--  WHERE id = 1;

-- ============================================================
-- Referencja: stara lista (kopia z assets/tv_channels_with_streams.json)
-- ============================================================
-- TVP1 HD               https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8
-- Polsat                https://lb2-e2-19.pluscdn.pl/ch/1502600/308/dash/20a18c30/live.mpd
-- Polsat News Polityka  https://lb2-e3-20.pluscdn.pl/lv/1511888/322/dash/52a9b70b/live.mpd
-- 4Fun TV               https://stream.4fun.tv:8888/hls/4f.m3u8
-- TV4                   https://lb2-e2-32.pluscdn.pl/ch/1502601/309/dash/e25c2c93/live.mpd
-- Polsat News           https://cdn-s-lb2.pluscdn.pl/lv/1517830/349/hls/f03a76f3/masterlist.m3u8
-- TVP Sport             https://cdndai.pl/tvpsport/index.m3u8
-- TVN24                 https://sl.cdn.tvn24.pl/tvn24-p-4e6b93e44150407ba69c8614a9c0fb29/live.m3u8
-- TVP3                  https://cdndai.pl/tvp3hd/index.m3u8
