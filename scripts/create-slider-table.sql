-- =====================================================
-- SUPABASE: Tabela slider_movies (10 slotów na slider)
-- =====================================================
-- Uruchom ten SQL w Supabase Dashboard:
-- Table Editor → SQL Editor → New Query
-- =====================================================

CREATE TABLE IF NOT EXISTS slider_movies (
  id SERIAL PRIMARY KEY,
  slot_position INTEGER NOT NULL CHECK (slot_position >= 1 AND slot_position <= 10),
  movie_id TEXT REFERENCES movies(id),

  -- Dane z TMDB (cached)
  tmdb_id INTEGER,
  title TEXT,
  backdrop_url TEXT,
  poster_url TEXT,
  logo_url TEXT,
  short_description TEXT,
  tmdb_overview TEXT,
  release_year TEXT,
  runtime INTEGER,
  vote_average REAL,
  genres JSONB DEFAULT '[]',

  -- Dodatkowe dane TMDB
  tmdb_directors JSONB DEFAULT '[]',
  tmdb_cast JSONB DEFAULT '[]',
  tmdb_videos JSONB DEFAULT '[]',

  -- Metadata
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  UNIQUE(slot_position)
);

-- Index
CREATE INDEX IF NOT EXISTS idx_slider_movies_position ON slider_movies(slot_position);

-- Trigger do auto-update updated_at
CREATE OR REPLACE FUNCTION update_slider_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS slider_movies_updated_at ON slider_movies;
CREATE TRIGGER slider_movies_updated_at
    BEFORE UPDATE ON slider_movies
    FOR EACH ROW
    EXECUTE FUNCTION update_slider_updated_at();

-- Wstaw puste sloty 1-10
INSERT INTO slider_movies (slot_position)
VALUES (1), (2), (3), (4), (5), (6), (7), (8), (9), (10)
ON CONFLICT (slot_position) DO NOTHING;

-- =====================================================
-- GOTOWE! Teraz masz 10 slotów do wypełnienia filmami
-- =====================================================
