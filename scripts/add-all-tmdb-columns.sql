-- =====================================================
-- SUPABASE: Wszystkie kolumny TMDB dla tabeli movies
-- =====================================================
-- Uruchom ten SQL w Supabase Dashboard:
-- Table Editor → SQL Editor → New Query
-- =====================================================

-- Podstawowe dane TMDB
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_id INTEGER;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_title TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_original_title TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_overview TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_tagline TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_release_date TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_runtime INTEGER;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_vote_average REAL;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_vote_count INTEGER;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_popularity REAL;

-- Obrazy
ALTER TABLE movies ADD COLUMN IF NOT EXISTS backdrop_url TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_poster_url TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_logo_url TEXT;

-- Gatunki (jako JSON array)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_genres JSONB DEFAULT '[]';

-- Ekipa filmowa (jako JSON array)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_directors JSONB DEFAULT '[]';
-- Format: [{"name": "Christopher Nolan", "id": 123}]

ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_writers JSONB DEFAULT '[]';
-- Format: [{"name": "Jonathan Nolan", "job": "Screenplay", "id": 456}]

ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_producers JSONB DEFAULT '[]';
-- Format: [{"name": "Emma Thomas", "id": 789}]

-- Obsada (jako JSON array)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_cast JSONB DEFAULT '[]';
-- Format: [{"name": "Leonardo DiCaprio", "character": "Dom Cobb", "profile_path": "/xxx.jpg", "id": 123, "order": 0}]

-- Wszystkie obrazy (jako JSON arrays)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_backdrops JSONB DEFAULT '[]';
-- Format: [{"file_path": "/xxx.jpg", "width": 1920, "height": 1080}]

ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_posters JSONB DEFAULT '[]';
-- Format: [{"file_path": "/xxx.jpg", "width": 500, "height": 750}]

ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_logos JSONB DEFAULT '[]';
-- Format: [{"file_path": "/xxx.png", "width": 500, "height": 200}]

-- Wideo/trailery (jako JSON array)
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_videos JSONB DEFAULT '[]';
-- Format: [{"key": "youtube_id", "name": "Official Trailer", "type": "Trailer", "site": "YouTube"}]

-- Dodatkowe info
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_budget INTEGER;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_revenue INTEGER;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_status TEXT;
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_imdb_id TEXT;

-- Timestamp ostatniej synchronizacji
ALTER TABLE movies ADD COLUMN IF NOT EXISTS tmdb_synced_at TIMESTAMPTZ;

-- Indexy
CREATE INDEX IF NOT EXISTS idx_movies_tmdb_id ON movies(tmdb_id);
CREATE INDEX IF NOT EXISTS idx_movies_tmdb_synced ON movies(tmdb_synced_at);

-- =====================================================
-- GOTOWE! Teraz możesz zapisać wszystkie dane z TMDB
-- =====================================================
