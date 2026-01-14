-- =====================================================
-- SUPABASE: Tabela movies dla Kino Play
-- =====================================================
-- Uruchom ten SQL w Supabase Dashboard:
-- Table Editor → SQL Editor → New Query
-- =====================================================

-- 1. Utworzenie tabeli movies
CREATE TABLE movies (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    runtime TEXT,
    genre TEXT,
    short_description TEXT,
    price INTEGER DEFAULT 0,
    poster_url TEXT,
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Indexy dla szybkiego wyszukiwania i sortowania
CREATE INDEX idx_movies_order ON movies(display_order);
CREATE INDEX idx_movies_genre ON movies(genre);
CREATE INDEX idx_movies_active ON movies(is_active);
CREATE INDEX idx_movies_title ON movies(title);

-- 3. Trigger do automatycznej aktualizacji updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER movies_updated_at_trigger
    BEFORE UPDATE ON movies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 4. Row Level Security (RLS)
ALTER TABLE movies ENABLE ROW LEVEL SECURITY;

-- Policy: Wszyscy mogą czytać aktywne filmy (dla aplikacji TV)
CREATE POLICY "Public read active movies" ON movies
    FOR SELECT USING (is_active = true);

-- Policy: Zalogowani użytkownicy mogą wszystko (dla admin panelu)
CREATE POLICY "Authenticated users full access" ON movies
    FOR ALL USING (auth.role() = 'authenticated');

-- =====================================================
-- UWAGA: Po uruchomieniu tego SQL:
-- 1. Przejdź do Authentication → Users → Add user
-- 2. Utwórz użytkownika admin z emailem i hasłem
-- 3. Uruchom skrypt import-movies.js
-- =====================================================
