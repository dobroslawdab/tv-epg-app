/**
 * Import filmów z filmy_play.json do Supabase
 *
 * Użycie:
 * 1. npm install @supabase/supabase-js
 * 2. Ustaw SUPABASE_SERVICE_ROLE_KEY poniżej (znajdziesz w Supabase → Settings → API)
 * 3. node import-movies.js
 */

const fs = require('fs');
const path = require('path');

// Supabase credentials
const SUPABASE_URL = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
// WAŻNE: Użyj service_role key (nie anon key!) - znajdziesz w Settings → API → service_role
const SUPABASE_SERVICE_ROLE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

async function importMovies() {
    // Dynamiczny import dla ES modules
    const { createClient } = await import('@supabase/supabase-js');

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

    // Ścieżka do pliku JSON
    const jsonPath = path.join(__dirname, '..', '..', 'Downloads', 'filmy_play.json');

    console.log(`Reading movies from: ${jsonPath}`);

    let movies;
    try {
        const rawData = fs.readFileSync(jsonPath, 'utf8');
        movies = JSON.parse(rawData);
        console.log(`Loaded ${movies.length} movies from JSON`);
    } catch (error) {
        // Alternatywna ścieżka
        const altPath = '/Users/uxellenceuxe/Downloads/filmy_play.json';
        console.log(`Trying alternative path: ${altPath}`);
        const rawData = fs.readFileSync(altPath, 'utf8');
        movies = JSON.parse(rawData);
        console.log(`Loaded ${movies.length} movies from JSON`);
    }

    // Mapowanie pól JSON na kolumny w bazie danych
    const dbMovies = movies.map(m => ({
        id: m.id,
        url: m.url,
        title: m.title,
        runtime: m.runtime || null,
        genre: m.genre || null,
        short_description: m.shortDescription || null,
        price: m.price || 0,
        poster_url: m.posterUrl || null,
        display_order: m.order || 0,
        is_active: true
    }));

    // Import w partiach po 100 (limit Supabase)
    const BATCH_SIZE = 100;
    let successCount = 0;
    let errorCount = 0;

    console.log(`\nStarting import in batches of ${BATCH_SIZE}...`);

    for (let i = 0; i < dbMovies.length; i += BATCH_SIZE) {
        const batch = dbMovies.slice(i, i + BATCH_SIZE);

        const { data, error } = await supabase
            .from('movies')
            .upsert(batch, { onConflict: 'id' });

        if (error) {
            console.error(`❌ Batch ${Math.floor(i/BATCH_SIZE) + 1}: ${error.message}`);
            errorCount += batch.length;
        } else {
            successCount += batch.length;
            const progress = ((i + batch.length) / dbMovies.length * 100).toFixed(1);
            console.log(`✓ Imported ${i + batch.length}/${dbMovies.length} (${progress}%)`);
        }
    }

    console.log(`\n========================================`);
    console.log(`Import completed!`);
    console.log(`✓ Success: ${successCount} movies`);
    console.log(`✗ Errors: ${errorCount} movies`);
    console.log(`========================================`);
}

// Uruchom import
importMovies().catch(console.error);
