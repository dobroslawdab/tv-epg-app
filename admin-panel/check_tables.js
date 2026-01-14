const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
const supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

const supabase = createClient(supabaseUrl, supabaseServiceKey);

async function main() {
  // Check if homepage_sections table exists
  const { data: sections, error: sectionsError } = await supabase
    .from('homepage_sections')
    .select('*')
    .limit(1);
  
  if (sectionsError) {
    console.log('homepage_sections table:', sectionsError.message);
  } else {
    console.log('homepage_sections exists, columns:', Object.keys(sections[0] || {}));
  }

  // Check if section_movies table exists
  const { data: sectionMovies, error: smError } = await supabase
    .from('section_movies')
    .select('*')
    .limit(1);
  
  if (smError) {
    console.log('section_movies table:', smError.message);
  } else {
    console.log('section_movies exists, columns:', Object.keys(sectionMovies[0] || {}));
  }

  // Check channels table
  const { data: channels, error: chError } = await supabase
    .from('channels')
    .select('*')
    .limit(1);
  
  if (chError) {
    console.log('channels table:', chError.message);
  } else {
    console.log('channels exists, columns:', Object.keys(channels[0] || {}));
  }
}

main().catch(console.error);
