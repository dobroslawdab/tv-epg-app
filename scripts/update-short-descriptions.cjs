/**
 * Skrypt do aktualizacji short_description w tabeli movies
 * z pliku filmy_play.json
 *
 * Uruchomienie: node update-short-descriptions.cjs
 */

const fs = require('fs');
const { createClient } = require('@supabase/supabase-js');

// Supabase config
const SUPABASE_URL = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
const SUPABASE_SERVICE_ROLE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

// JSON path
const JSON_PATH = '/Users/uxellenceuxe/Downloads/filmy_play.json';

async function updateShortDescriptions() {
    console.log('📖 Wczytuję JSON...');

    const jsonData = fs.readFileSync(JSON_PATH, 'utf8');
    const movies = JSON.parse(jsonData);

    console.log(`📦 Znaleziono ${movies.length} filmów w JSON`);

    let updated = 0;
    let errors = 0;
    const BATCH_SIZE = 50;

    for (let i = 0; i < movies.length; i += BATCH_SIZE) {
        const batch = movies.slice(i, i + BATCH_SIZE);

        for (const movie of batch) {
            if (!movie.shortDescription || !movie.id) continue;

            const { error } = await supabase
                .from('movies')
                .update({ short_description: movie.shortDescription })
                .eq('id', movie.id);

            if (error) {
                console.error(`❌ Błąd dla ${movie.id}: ${error.message}`);
                errors++;
            } else {
                updated++;
            }
        }

        console.log(`✅ Zaktualizowano ${Math.min(i + BATCH_SIZE, movies.length)}/${movies.length}`);
    }

    console.log('\n========================================');
    console.log(`✅ Zaktualizowano: ${updated} filmów`);
    console.log(`❌ Błędy: ${errors}`);
    console.log('========================================');
}

updateShortDescriptions().catch(console.error);
