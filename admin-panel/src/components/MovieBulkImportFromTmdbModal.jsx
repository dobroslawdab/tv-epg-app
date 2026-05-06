import React, { useState } from 'react';
import { supabase } from '../supabase';

const TMDB_API_KEY = '716cc02044e4d92d0a012a426902cc2d';
const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

/**
 * Bulk import wielu filmów z TMDB jednorazowo.
 *
 * Tryby:
 *  - SEARCH: wpisz keyword (tytuł/aktor/franczyza), TMDB zwraca max 60 filmów (3 strony × 20),
 *    wybierz checkboxami które zaimportować.
 *  - PERSON: wyszukaj osobę (np. "Jason Statham"), pobierz jej filmografię, wybierz filmy.
 *  - IDS: wklej listę TMDB IDs (po przecinku/nowej linii), import wszystkich.
 */
export default function MovieBulkImportFromTmdbModal({ open, onClose, onSaved }) {
  const [mode, setMode] = useState('search');  // 'search' | 'person' | 'ids'
  const [query, setQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState([]);    // [{tmdb_id, title, year, poster, overview}]
  const [selected, setSelected] = useState(new Set());  // Set<tmdb_id>
  const [defaultPrice, setDefaultPrice] = useState('19');
  const [defaultGenre, setDefaultGenre] = useState('');
  const [importing, setImporting] = useState(false);
  const [importLog, setImportLog] = useState([]);  // [{title, status: 'ok'|'skip'|'err', message?}]
  const [error, setError] = useState('');

  if (!open) return null;

  const reset = () => {
    setQuery(''); setResults([]); setSelected(new Set());
    setImporting(false); setImportLog([]); setError('');
  };

  const close = () => { reset(); onClose(); };

  const toggleSelect = (id) => {
    const newSet = new Set(selected);
    newSet.has(id) ? newSet.delete(id) : newSet.add(id);
    setSelected(newSet);
  };

  const selectAll = () => setSelected(new Set(results.map(r => r.tmdb_id)));
  const deselectAll = () => setSelected(new Set());

  const handleSearch = async () => {
    if (!query.trim()) return;
    setSearching(true); setError(''); setResults([]); setSelected(new Set());
    try {
      let tmdbResults = [];

      if (mode === 'search') {
        // Multi-page search (max 3 pages = 60 results)
        for (let page = 1; page <= 3; page++) {
          const url = `https://api.themoviedb.org/3/search/movie?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(query)}&language=pl-PL&page=${page}`;
          const res = await fetch(url);
          if (!res.ok) break;
          const data = await res.json();
          tmdbResults.push(...(data.results || []));
          if (page >= (data.total_pages || 1)) break;
        }
      } else if (mode === 'person') {
        // 1) Find person ID
        const findUrl = `https://api.themoviedb.org/3/search/person?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(query)}&language=pl-PL`;
        const findRes = await fetch(findUrl);
        const findData = await findRes.json();
        const person = findData.results?.[0];
        if (!person) throw new Error('Nie znaleziono osoby');
        // 2) Fetch person's movie credits
        const credUrl = `https://api.themoviedb.org/3/person/${person.id}/movie_credits?api_key=${TMDB_API_KEY}&language=pl-PL`;
        const credRes = await fetch(credUrl);
        const credData = await credRes.json();
        tmdbResults = (credData.cast || []).sort((a, b) => (b.popularity || 0) - (a.popularity || 0)).slice(0, 50);
      } else if (mode === 'ids') {
        // Parse IDs (split by comma/newline/space)
        const ids = query.split(/[\s,]+/).map(s => s.trim()).filter(s => /^\d+$/.test(s));
        if (!ids.length) throw new Error('Brak prawidłowych TMDB IDs');
        // Fetch each one (parallel, max 20 at once)
        const fetchPromises = ids.slice(0, 50).map(id =>
          fetch(`https://api.themoviedb.org/3/movie/${id}?api_key=${TMDB_API_KEY}&language=pl-PL`)
            .then(r => r.ok ? r.json() : null)
        );
        const fetched = await Promise.all(fetchPromises);
        tmdbResults = fetched.filter(Boolean);
      }

      // Normalize results
      setResults(tmdbResults.map(r => ({
        tmdb_id: r.id,
        title: r.title || r.original_title,
        year: (r.release_date || '').slice(0, 4),
        poster: r.poster_path ? `${TMDB_IMAGE_BASE}/w200${r.poster_path}` : null,
        overview: r.overview || '',
        popularity: r.popularity || 0
      })));
    } catch (e) {
      setError('Błąd: ' + e.message);
    } finally {
      setSearching(false);
    }
  };

  const fetchFullDetails = async (tmdbId) => {
    const url = `https://api.themoviedb.org/3/movie/${tmdbId}?api_key=${TMDB_API_KEY}&language=pl-PL&append_to_response=images,videos,credits&include_image_language=pl,en,null`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`TMDB ${res.status}`);
    return res.json();
  };

  const buildPayload = (details) => {
    const posterPath = details.poster_path ? `${TMDB_IMAGE_BASE}/original${details.poster_path}` : null;
    const backdropPath = details.backdrop_path ? `${TMDB_IMAGE_BASE}/original${details.backdrop_path}` : null;
    const logoPath = details.images?.logos?.[0]?.file_path ? `${TMDB_IMAGE_BASE}/original${details.images.logos[0].file_path}` : null;

    const directors = (details.credits?.crew || []).filter(c => c.job === 'Director').map(c => ({ id: c.id, name: c.name }));
    const writers = (details.credits?.crew || []).filter(c => c.department === 'Writing').map(c => ({ id: c.id, name: c.name, job: c.job }));
    const producers = (details.credits?.crew || []).filter(c => c.job === 'Producer').map(c => ({ id: c.id, name: c.name }));
    const cast = (details.credits?.cast || []).slice(0, 15).map(c => ({ id: c.id, name: c.name, character: c.character, order: c.order, profile_path: c.profile_path }));

    return {
      id: `tmdb_${details.id}`,
      url: `https://playnow.pl/tvod/tmdb_${details.id}`,
      title: details.title,
      runtime: details.runtime ? `${details.runtime} min.` : '',
      genre: defaultGenre || (details.genres?.[0]?.name || ''),
      short_description: details.overview || '',
      price: parseInt(defaultPrice, 10) || 19,
      poster_url: posterPath || '',
      display_order: 0,
      is_active: true,
      backdrop_url: backdropPath,
      tmdb_id: details.id,
      tmdb_title: details.title,
      tmdb_original_title: details.original_title,
      tmdb_overview: details.overview,
      tmdb_tagline: details.tagline,
      tmdb_release_date: details.release_date || null,
      tmdb_runtime: details.runtime,
      tmdb_vote_average: details.vote_average,
      tmdb_vote_count: details.vote_count,
      tmdb_popularity: details.popularity,
      tmdb_poster_url: posterPath,
      tmdb_logo_url: logoPath,
      tmdb_genres: details.genres || [],
      tmdb_directors: directors,
      tmdb_writers: writers,
      tmdb_producers: producers,
      tmdb_cast: cast,
      tmdb_backdrops: (details.images?.backdrops || []).slice(0, 10).map(b => ({ width: b.width, height: b.height, file_path: b.file_path })),
      tmdb_posters: (details.images?.posters || []).slice(0, 5).map(p => ({ width: p.width, height: p.height, file_path: p.file_path })),
      tmdb_logos: (details.images?.logos || []).slice(0, 5).map(l => ({ width: l.width, height: l.height, file_path: l.file_path })),
      tmdb_videos: details.videos || [],
      tmdb_budget: details.budget,
      tmdb_revenue: details.revenue,
      tmdb_status: details.status,
      tmdb_imdb_id: details.imdb_id,
      tmdb_synced_at: new Date().toISOString(),
      is_top10: false, is_slider: false, is_recommended: false,
      youtube_url: null, selected_logo_url: logoPath
    };
  };

  const handleImport = async () => {
    if (!selected.size) return;
    setImporting(true); setImportLog([]); setError('');

    const log = [];
    const toImport = results.filter(r => selected.has(r.tmdb_id));

    for (const movie of toImport) {
      try {
        // Skip if already exists by tmdb_id
        const { data: existing } = await supabase.from('movies').select('id').eq('tmdb_id', movie.tmdb_id).limit(1);
        if (existing?.length) {
          log.push({ title: movie.title, status: 'skip', message: 'Już w bazie' });
          setImportLog([...log]);
          continue;
        }
        const details = await fetchFullDetails(movie.tmdb_id);
        const payload = buildPayload(details);
        const { error: insertErr } = await supabase.from('movies').insert(payload);
        if (insertErr) {
          log.push({ title: movie.title, status: 'err', message: insertErr.message });
        } else {
          log.push({ title: movie.title, status: 'ok' });
        }
      } catch (e) {
        log.push({ title: movie.title, status: 'err', message: e.message });
      }
      setImportLog([...log]);
      // Small delay between imports to avoid rate limiting
      await new Promise(r => setTimeout(r, 200));
    }
    setImporting(false);
    onSaved?.();
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
    }}>
      <div style={{
        background: '#1e1e2e', color: '#eee', borderRadius: 12, padding: 24,
        width: '90%', maxWidth: 1100, maxHeight: '90vh', overflow: 'auto'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ margin: 0 }}>Bulk import z TMDB</h2>
          <button onClick={close} style={{ background: 'transparent', color: '#eee', border: '1px solid #444', borderRadius: 6, padding: '6px 12px', cursor: 'pointer' }}>✕</button>
        </div>

        {/* Mode selector */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          {[
            { v: 'search', l: '🔍 Tytuł / keyword' },
            { v: 'person', l: '👤 Aktor / reżyser' },
            { v: 'ids', l: '🆔 TMDB IDs' }
          ].map(({ v, l }) => (
            <button key={v} onClick={() => { setMode(v); setResults([]); setQuery(''); }}
              style={{
                background: mode === v ? '#48227c' : '#2a2a3e',
                color: '#eee', border: '1px solid #444', borderRadius: 6,
                padding: '8px 16px', cursor: 'pointer'
              }}>{l}</button>
          ))}
        </div>

        {/* Query input */}
        {mode === 'ids' ? (
          <textarea
            value={query} onChange={e => setQuery(e.target.value)}
            placeholder="Wpisz TMDB IDs po przecinku lub nowych liniach (np. 27205, 155, 102382)"
            style={{ width: '100%', minHeight: 80, padding: 12, borderRadius: 6, background: '#2a2a3e', color: '#eee', border: '1px solid #444', fontFamily: 'monospace' }}
          />
        ) : (
          <input
            type="text" value={query} onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            placeholder={mode === 'person' ? 'Nazwisko aktora/reżysera (np. Jason Statham)' : 'Tytuł / keyword (np. Star Wars)'}
            style={{ width: '100%', padding: 12, borderRadius: 6, background: '#2a2a3e', color: '#eee', border: '1px solid #444' }}
          />
        )}

        {/* Default values */}
        <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
          <label style={{ flex: 1 }}>
            <span style={{ display: 'block', fontSize: 12, color: '#aaa', marginBottom: 4 }}>Domyślna cena (zł)</span>
            <input type="number" value={defaultPrice} onChange={e => setDefaultPrice(e.target.value)}
              style={{ width: '100%', padding: 8, borderRadius: 6, background: '#2a2a3e', color: '#eee', border: '1px solid #444' }} />
          </label>
          <label style={{ flex: 1 }}>
            <span style={{ display: 'block', fontSize: 12, color: '#aaa', marginBottom: 4 }}>Domyślny gatunek (puste = z TMDB)</span>
            <input type="text" value={defaultGenre} onChange={e => setDefaultGenre(e.target.value)} placeholder="np. Akcja"
              style={{ width: '100%', padding: 8, borderRadius: 6, background: '#2a2a3e', color: '#eee', border: '1px solid #444' }} />
          </label>
        </div>

        <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
          <button onClick={handleSearch} disabled={searching || !query.trim()}
            style={{ background: '#5fedd4', color: '#000', border: 'none', borderRadius: 6, padding: '10px 20px', cursor: 'pointer', fontWeight: 'bold' }}>
            {searching ? 'Szukam…' : 'Szukaj'}
          </button>
        </div>

        {error && <div style={{ marginTop: 12, padding: 12, background: '#5e2a2a', borderRadius: 6 }}>{error}</div>}

        {/* Results */}
        {results.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <span>Znaleziono <b>{results.length}</b>, zaznaczono <b>{selected.size}</b></span>
              <div style={{ display: 'flex', gap: 8 }}>
                <button onClick={selectAll} style={{ background: '#2a2a3e', color: '#eee', border: '1px solid #444', borderRadius: 6, padding: '6px 12px', cursor: 'pointer' }}>Zaznacz wszystkie</button>
                <button onClick={deselectAll} style={{ background: '#2a2a3e', color: '#eee', border: '1px solid #444', borderRadius: 6, padding: '6px 12px', cursor: 'pointer' }}>Odznacz</button>
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: 12 }}>
              {results.map(r => (
                <label key={r.tmdb_id} style={{
                  background: selected.has(r.tmdb_id) ? '#48227c' : '#2a2a3e',
                  border: selected.has(r.tmdb_id) ? '2px solid #5fedd4' : '1px solid #444',
                  borderRadius: 8, padding: 8, cursor: 'pointer', display: 'block'
                }}>
                  <input type="checkbox" checked={selected.has(r.tmdb_id)} onChange={() => toggleSelect(r.tmdb_id)} style={{ marginRight: 6 }} />
                  {r.poster && <img src={r.poster} alt={r.title} style={{ width: '100%', borderRadius: 4, marginBottom: 6 }} />}
                  <div style={{ fontSize: 13, fontWeight: 'bold' }}>{r.title}</div>
                  <div style={{ fontSize: 11, color: '#aaa' }}>{r.year} · ID {r.tmdb_id}</div>
                </label>
              ))}
            </div>

            <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
              <button onClick={handleImport} disabled={importing || !selected.size}
                style={{ background: '#5fedd4', color: '#000', border: 'none', borderRadius: 6, padding: '12px 24px', cursor: 'pointer', fontWeight: 'bold', fontSize: 16 }}>
                {importing ? `Importuję… (${importLog.length}/${selected.size})` : `Importuj ${selected.size} filmów`}
              </button>
              {importing && <span style={{ color: '#aaa' }}>Nie zamykaj okna do końca importu.</span>}
            </div>

            {/* Import log */}
            {importLog.length > 0 && (
              <div style={{ marginTop: 16, padding: 12, background: '#2a2a3e', borderRadius: 6, maxHeight: 200, overflow: 'auto', fontFamily: 'monospace', fontSize: 12 }}>
                {importLog.map((l, i) => (
                  <div key={i} style={{ color: l.status === 'ok' ? '#5fedd4' : l.status === 'skip' ? '#aaa' : '#e88' }}>
                    {l.status === 'ok' ? '✓' : l.status === 'skip' ? '○' : '✗'} {l.title}{l.message ? ` — ${l.message}` : ''}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
