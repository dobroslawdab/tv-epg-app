const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
const supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

const supabase = createClient(supabaseUrl, supabaseServiceKey);

async function setupHomepage() {
  console.log('Setting up homepage sections...');

  // Check current sections
  const { data: existing, error: checkError } = await supabase
    .from('homepage_sections')
    .select('*');

  if (checkError) {
    console.error('Error checking sections:', checkError.message);
    return;
  }

  console.log('Existing sections:', existing?.length || 0);

  if (existing && existing.length > 0) {
    console.log('Sections already exist:');
    existing.forEach(s => console.log(` - ${s.name} (${s.source_type || 'manual'})`));
    return;
  }

  // Create default sections for "Start" page
  const defaultSections = [
    {
      name: 'Hero Slider',
      type: 'slider',
      source_type: 'manual',
      display_order: 0,
      is_active: true
    },
    {
      name: 'Nowości',
      type: 'horizontal',
      source_type: 'newest',
      display_order: 1,
      is_active: true
    },
    {
      name: 'Polecane',
      type: 'horizontal',
      source_type: 'manual',
      display_order: 2,
      is_active: true
    },
    {
      name: 'Top 10',
      type: 'top10',
      source_type: 'manual',
      display_order: 3,
      is_active: true
    },
    {
      name: 'Akcja',
      type: 'horizontal',
      source_type: 'category',
      category_filter: 'Akcja',
      display_order: 4,
      is_active: true
    },
    {
      name: 'Komedie',
      type: 'horizontal',
      source_type: 'category',
      category_filter: 'Komedia',
      display_order: 5,
      is_active: true
    },
    {
      name: 'Horrory',
      type: 'horizontal',
      source_type: 'category',
      category_filter: 'Horror',
      display_order: 6,
      is_active: true
    },
    {
      name: 'Dla dzieci',
      type: 'horizontal',
      source_type: 'category',
      category_filter: 'Familijny',
      display_order: 7,
      is_active: true
    },
  ];

  const { data: inserted, error: insertError } = await supabase
    .from('homepage_sections')
    .insert(defaultSections)
    .select();

  if (insertError) {
    console.error('Error inserting sections:', insertError.message);
    console.log('\nMissing columns? Run this SQL in Supabase Dashboard:\n');
    console.log(`
-- Add missing columns to homepage_sections
ALTER TABLE homepage_sections ADD COLUMN IF NOT EXISTS source_type VARCHAR(50) DEFAULT 'manual';
ALTER TABLE homepage_sections ADD COLUMN IF NOT EXISTS category_filter VARCHAR(100);
    `);
    return;
  }

  console.log('✅ Created', inserted.length, 'sections:');
  inserted.forEach(s => console.log(` - ${s.name} (${s.source_type})`));
}

setupHomepage().catch(console.error);
