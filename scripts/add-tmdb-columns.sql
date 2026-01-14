-- =====================================================
-- SUPABASE: Dodaj kolumny TMDB do tabeli movies
-- =====================================================
-- Uruchom ten SQL w Supabase Dashboard:
-- Table Editor → SQL Editor → New Query
-- =====================================================

ALTER TABLE movies ADD COLUMN IF NOT EXISTS backdrop_url TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_id INTEGER;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_overview TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_vote_average REAL;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_release_date TEXT;

-- Opcjonalnie: index na tmdb_id
CREATE INDEX IF NOT EXISTS idx_movies_tmdb_id ON movies(tmdb_id);
