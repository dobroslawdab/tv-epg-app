import { createClient } from '@supabase/supabase-js';

const supabaseUrl = 'https://kexrkaqxoadxugnnbnjh.supabase.co';
// Service role key - pełne uprawnienia (tylko dla admin panelu)
const supabaseServiceKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo';

export const supabase = createClient(supabaseUrl, supabaseServiceKey);
