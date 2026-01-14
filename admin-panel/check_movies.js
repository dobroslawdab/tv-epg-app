const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const supabaseUrl = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
const supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

const supabase = createClient(supabaseUrl, supabaseServiceKey);

const movies = JSON.parse(fs.readFileSync('./movies.json', 'utf8'));

async function main() {
  // Get ALL existing movies
  let allExisting = [];
  let from = 0;
  const pageSize = 1000;
  
  while (true) {
    const { data, error } = await supabase
      .from('movies')
      .select('id')
      .range(from, from + pageSize - 1);
    
    if (error) {
      console.log('Error fetching existing movies:', error.message);
      return;
    }
    
    allExisting = allExisting.concat(data || []);
    
    if (!data || data.length < pageSize) break;
    from += pageSize;
  }
  
  const existingIds = new Set(allExisting.map(m => String(m.id)));
  console.log('Total existing movies in DB:', allExisting.length);
  console.log('Sample existing IDs:', Array.from(existingIds).slice(0, 5));
  
  // Find missing movies and convert to snake_case
  const missing = movies
    .filter(m => !existingIds.has(String(m.id)))
    .map(({order, posterUrl, shortDescription, ...rest}) => ({
      ...rest,
      poster_url: posterUrl,
      short_description: shortDescription
    }));
  
  console.log('Missing movies to add:', missing.length);
  
  if (missing.length === 0) {
    console.log('All movies already exist in database!');
    return;
  }
  
  console.log('Missing movie titles:');
  missing.forEach(m => console.log(' -', m.title));
  
  // Add missing movies one by one to see which one fails
  let successCount = 0;
  for (const movie of missing) {
    const { error: insertError } = await supabase
      .from('movies')
      .insert(movie);
    
    if (insertError) {
      console.log(`Failed to add "${movie.title}": ${insertError.message}`);
    } else {
      successCount++;
    }
  }
  
  console.log(`Successfully added ${successCount} movies`);
}

main().catch(console.error);
