-- Dodaj kolumny do tabeli movies dla funkcjonalności Top 10 i sortowania
-- Uruchom ten skrypt w Supabase SQL Editor

-- Kolumna is_top10 - czy film jest w Top 10
ALTER TABLE movies
ADD COLUMN IF NOT EXISTS is_top10 BOOLEAN DEFAULT FALSE;

-- Kolumna top10_order - kolejność w Top 10 (1-10)
ALTER TABLE movies
ADD COLUMN IF NOT EXISTS top10_order INTEGER;

-- Kolumna created_at - data dodania filmu (dla sortowania "najnowsze")
ALTER TABLE movies
ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

-- Indeksy dla szybkiego filtrowania
CREATE INDEX IF NOT EXISTS idx_movies_is_top10 ON movies(is_top10) WHERE is_top10 = TRUE;
CREATE INDEX IF NOT EXISTS idx_movies_created_at ON movies(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_movies_genre ON movies(genre);

-- Komentarze:
-- is_top10: TRUE = film znajduje się na liście Top 10
-- top10_order: 1-10, określa pozycję na liście Top 10
-- created_at: data dodania, używana do sortowania "od najnowszych"
