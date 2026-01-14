const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
const supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

const supabase = createClient(supabaseUrl, supabaseServiceKey);

async function createTables() {
  // Create homepage_sections table using RPC or direct SQL
  // Since we can't run DDL via JS client, we'll create sample data that will auto-create schema
  
  // First, let's try to insert into homepage_sections
  const { error: sectionsError } = await supabase.rpc('create_homepage_tables', {});
  
  if (sectionsError) {
    console.log('RPC not available, creating via Supabase Dashboard required');
    console.log('SQL to run in Supabase SQL Editor:');
    console.log(`
-- Create homepage_sections table
CREATE TABLE IF NOT EXISTS homepage_sections (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) DEFAULT 'horizontal',
  display_order INTEGER DEFAULT 0,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create section_movies junction table
CREATE TABLE IF NOT EXISTS section_movies (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  section_id UUID REFERENCES homepage_sections(id) ON DELETE CASCADE,
  movie_id VARCHAR(50) NOT NULL,
  display_order INTEGER DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  UNIQUE(section_id, movie_id)
);

-- Enable RLS
ALTER TABLE homepage_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE section_movies ENABLE ROW LEVEL SECURITY;

-- Allow all operations for service role
CREATE POLICY "Allow all for service role" ON homepage_sections FOR ALL USING (true);
CREATE POLICY "Allow all for service role" ON section_movies FOR ALL USING (true);

-- Insert sample sections
INSERT INTO homepage_sections (name, type, display_order, is_active) VALUES
  ('Hero Slider', 'slider', 0, true),
  ('Nowości', 'horizontal', 1, true),
  ('Polecane', 'horizontal', 2, true),
  ('Top 10', 'top10', 3, true),
  ('Akcja', 'horizontal', 4, true),
  ('Komedie', 'horizontal', 5, true),
  ('Horrory', 'horizontal', 6, true),
  ('Dla dzieci', 'horizontal', 7, true);
    `);
  }
}

createTables();
