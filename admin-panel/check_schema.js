const { createClient } = require('@supabase/supabase-js');

const supabaseUrl = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
const supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

const supabase = createClient(supabaseUrl, supabaseServiceKey);

async function main() {
  const { data, error } = await supabase
    .from('movies')
    .select('*')
    .limit(1);
  
  if (error) {
    console.log('Error:', error.message);
    return;
  }
  
  console.log('Columns:', Object.keys(data[0] || {}));
}

main().catch(console.error);
