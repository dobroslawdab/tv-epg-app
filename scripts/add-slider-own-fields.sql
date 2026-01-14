-- Dodaj kolumny dla własnych danych z tabeli movies
-- Uruchom ten skrypt w Supabase SQL Editor

ALTER TABLE slider_movies
ADD COLUMN IF NOT EXISTS own_genre TEXT,
ADD COLUMN IF NOT EXISTS own_runtime TEXT;

-- Komentarz:
-- own_genre - gatunek z tabeli movies (np. "Horror, Akcja")
-- own_runtime - czas trwania z tabeli movies (np. "124 min")
