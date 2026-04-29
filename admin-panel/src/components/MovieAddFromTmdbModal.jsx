import React, { useState } from 'react';
import { supabase } from '../supabase';

const TMDB_API_KEY = '716cc02044e4d92d0a012a426902cc2d';
const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

/**
 * Modal: dodaj nowy film do bazy z TMDB.
 * Flow: search → wybór → preview + uzupełnij pola PlayNow → zapisz.
 */
export default function MovieAddFromTmdbModal({ open, onClose, onSaved }) {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');

  // Selected TMDB movie + full details
  const [selected, setSelected] = useState(null);
  const [details, setDetails] = useState(null);
  const [loadingDetails, setLoadingDetails] = useState(false);

  // PlayNow custom fields (po wyborze TMDB)
  const [customId, setCustomId] = useState('');
  const [customUrl, setCustomUrl] = useState('');
  const [customPrice, setCustomPrice] = useState('');
  const [customGenre, setCustomGenre] = useState('');
  const [customPosterUrl, setCustomPosterUrl] = useState('');
  const [customTrailerUrl, setCustomTrailerUrl] = useState('');
  const [selectedPosterPath, setSelectedPosterPath] = useState(null);  // wybrany plakat z TMDB images
  const [saving, setSaving] = useState(false);

  if (!open) return null;

  const reset = () => {
    setSearchQuery('');
    setSearchResults([]);
    setSelected(null);
    setDetails(null);
    setCustomId('');
    setCustomUrl('');
    setCustomPrice('');
    setCustomGenre('');
    setCustomPosterUrl('');
    setCustomTrailerUrl('');
    setSelectedPosterPath(null);
    setError('');
  };

  const close = () => {
    reset();
    onClose();
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setSearching(true);
    setError('');
    setSearchResults([]);
    try {
      // Detect: integer = direct TMDB ID lookup, otherwise text search
      const isId = /^\d+$/.test(searchQuery.trim());
      if (isId) {
        const url = `https://api.themoviedb.org/3/movie/${searchQuery.trim()}?api_key=${TMDB_API_KEY}&language=pl-PL`;
        const res = await fetch(url);
        if (!res.ok) throw new Error(`TMDB ${res.status}`);
        const data = await res.json();
        setSearchResults([data]);
      } else {
        const url = `https://api.themoviedb.org/3/search/movie?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(searchQuery)}&language=pl-PL`;
        const res = await fetch(url);
        if (!res.ok) throw new Error(`TMDB ${res.status}`);
        const data = await res.json();
        setSearchResults(data.results || []);
      }
    } catch (e) {
      setError('Błąd TMDB: ' + e.message);
    } finally {
      setSearching(false);
    }
  };

  const handleSelect = async (tmdbMovie) => {
    setSelected(tmdbMovie);
    setLoadingDetails(true);
    try {
      const detailsUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}?api_key=${TMDB_API_KEY}&language=pl-PL&append_to_response=credits`;
      const imagesUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/images?api_key=${TMDB_API_KEY}`;
      const videosUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/videos?api_key=${TMDB_API_KEY}`;

      const [d, i, v] = await Promise.all([
        fetch(detailsUrl).then(r => r.json()),
        fetch(imagesUrl).then(r => r.json()),
        fetch(videosUrl).then(r => r.json())
      ]);

      const merged = { ...d, images: i, videos: v.results || [] };
      setDetails(merged);

      // Pre-fill defaults
      const polishGenre = (d.genres?.[0]?.name) || '';
      setCustomGenre(polishGenre);
      setCustomPrice('19');  // default cena PlayNow
      setSelectedPosterPath(d.poster_path);  // domyślnie główny plakat z TMDB
    } catch (e) {
      setError('Błąd pobierania szczegółów TMDB: ' + e.message);
    } finally {
      setLoadingDetails(false);
    }
  };

  const handleSave = async () => {
    if (!details || !customId.trim()) {
      setError('Wymagane: wybierz film TMDB + podaj custom ID (np. PlayNow tvod ID)');
      return;
    }
    setSaving(true);
    setError('');
    try {
      // Mapuj TMDB metadane na rekord movies. Plakat: wybrany przez usera (selectedPosterPath)
      // lub domyślny (details.poster_path) jako fallback.
      const effectivePosterPath = selectedPosterPath || details.poster_path;
      const posterPath = effectivePosterPath ? `${TMDB_IMAGE_BASE}/original${effectivePosterPath}` : null;
      const backdropPath = details.backdrop_path ? `${TMDB_IMAGE_BASE}/original${details.backdrop_path}` : null;
      const logoPath = details.images?.logos?.[0]?.file_path
        ? `${TMDB_IMAGE_BASE}/original${details.images.logos[0].file_path}` : null;

      const directors = (details.credits?.crew || []).filter(c => c.job === 'Director')
        .map(c => ({ id: c.id, name: c.name }));
      const writers = (details.credits?.crew || []).filter(c => c.department === 'Writing')
        .map(c => ({ id: c.id, name: c.name, job: c.job }));
      const producers = (details.credits?.crew || []).filter(c => c.job === 'Producer')
        .map(c => ({ id: c.id, name: c.name }));
      const cast = (details.credits?.cast || []).slice(0, 15)
        .map(c => ({ id: c.id, name: c.name, character: c.character, order: c.order, profile_path: c.profile_path }));

      const payload = {
        id: customId.trim(),
        url: customUrl.trim() || `https://playnow.pl/tvod/${customId.trim()}`,
        title: details.title,
        runtime: details.runtime ? `${details.runtime} min.` : '',
        genre: customGenre || (details.genres?.[0]?.name || ''),
        short_description: details.overview || '',
        price: customPrice ? parseInt(customPrice, 10) : 19,
        poster_url: customPosterUrl.trim() || posterPath || '',
        display_order: 0,
        is_active: true,
        backdrop_url: backdropPath,
        // TMDB fields
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
        tmdb_backdrops: (details.images?.backdrops || []).slice(0, 10).map(b => ({
          width: b.width, height: b.height, file_path: b.file_path
        })),
        tmdb_posters: (details.images?.posters || []).slice(0, 5).map(p => ({
          width: p.width, height: p.height, file_path: p.file_path
        })),
        tmdb_logos: (details.images?.logos || []).slice(0, 5).map(l => ({
          width: l.width, height: l.height, file_path: l.file_path
        })),
        tmdb_videos: details.videos || [],
        tmdb_budget: details.budget,
        tmdb_revenue: details.revenue,
        tmdb_status: details.status,
        tmdb_imdb_id: details.imdb_id,
        tmdb_synced_at: new Date().toISOString(),
        is_top10: false,
        is_slider: false,
        is_recommended: false,
        youtube_url: customTrailerUrl.trim() || null,  // trailer dla slidera Kino Play
        selected_logo_url: logoPath
      };

      const { error: insertErr } = await supabase.from('movies').insert(payload);
      if (insertErr) throw insertErr;

      onSaved?.(payload);
      close();
    } catch (e) {
      setError('Błąd zapisu: ' + (e.message || e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={overlayStyle} onClick={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div style={modalStyle}>
        <div style={headerStyle}>
          <h2 style={{ margin: 0 }}>Dodaj film z TMDB</h2>
          <button onClick={close} style={closeBtnStyle}>✕</button>
        </div>

        {/* Krok 1: Szukaj */}
        {!selected && (
          <>
            <p style={{ color: '#aaa', marginTop: 8 }}>Wpisz tytuł filmu lub TMDB ID (np. <code>533533</code>):</p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
              <input
                type="text"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') handleSearch(); }}
                placeholder="np. Tron Ares lub 533533"
                style={inputStyle}
                autoFocus
              />
              <button onClick={handleSearch} disabled={searching} style={primaryBtn}>
                {searching ? 'Szukam…' : 'Szukaj'}
              </button>
            </div>

            {error && <div style={errorStyle}>{error}</div>}

            <div style={{ maxHeight: 400, overflowY: 'auto' }}>
              {searchResults.map(m => (
                <div key={m.id} style={resultRowStyle} onClick={() => handleSelect(m)}>
                  {m.poster_path && (
                    <img src={`${TMDB_IMAGE_BASE}/w92${m.poster_path}`} alt="" style={{ width: 60, marginRight: 12, borderRadius: 4 }} />
                  )}
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 'bold' }}>{m.title} <span style={{ color: '#888' }}>({m.release_date?.slice(0, 4) || '?'})</span></div>
                    <div style={{ fontSize: 12, color: '#aaa' }}>TMDB ID: {m.id} · ⭐ {m.vote_average?.toFixed(1) || '?'}</div>
                    <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>{m.overview?.slice(0, 150)}…</div>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {/* Krok 2: Preview + custom fields */}
        {selected && (
          <>
            {loadingDetails && <div style={{ padding: 20 }}>Ładowanie szczegółów…</div>}
            {details && (
              <div>
                <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
                  {details.poster_path && (
                    <img src={`${TMDB_IMAGE_BASE}/w185${details.poster_path}`} alt="" style={{ width: 120, borderRadius: 6 }} />
                  )}
                  <div style={{ flex: 1 }}>
                    <h3 style={{ margin: '0 0 8px 0' }}>{details.title}</h3>
                    <div style={{ color: '#aaa', fontSize: 13 }}>
                      TMDB {details.id} · {details.release_date?.slice(0, 4)} · {details.runtime} min · ⭐ {details.vote_average?.toFixed(1)}
                    </div>
                    <div style={{ marginTop: 8, fontSize: 13 }}>{details.overview?.slice(0, 300)}…</div>
                  </div>
                </div>

                {/* Wybór plakata z TMDB images.posters */}
                {details.images?.posters?.length > 0 && (
                  <div style={{ borderTop: '1px solid #333', paddingTop: 12, marginBottom: 16 }}>
                    <h4 style={{ marginTop: 0 }}>Wybierz plakat ({details.images.posters.length} dostępnych):</h4>
                    <div style={{
                      display: 'flex', gap: 8, overflowX: 'auto',
                      paddingBottom: 8, scrollbarWidth: 'thin'
                    }}>
                      {details.images.posters.map((p, idx) => {
                        const isPicked = selectedPosterPath === p.file_path;
                        return (
                          <img
                            key={idx}
                            src={`${TMDB_IMAGE_BASE}/w185${p.file_path}`}
                            alt=""
                            onClick={() => setSelectedPosterPath(p.file_path)}
                            style={{
                              height: 180, width: 'auto', borderRadius: 4,
                              border: isPicked ? '3px solid #5FEDD4' : '2px solid transparent',
                              cursor: 'pointer', flexShrink: 0,
                              opacity: isPicked ? 1 : 0.7,
                              transition: 'all 0.15s'
                            }}
                          />
                        );
                      })}
                    </div>
                    <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>
                      Wybrany plakat zostanie zapisany jako tmdb_poster_url i (jeśli nie podasz Custom poster URL) jako poster_url.
                    </div>
                  </div>
                )}

                <div style={{ borderTop: '1px solid #333', paddingTop: 12 }}>
                  <h4 style={{ marginTop: 0 }}>Pola PlayNow:</h4>

                  <label style={labelStyle}>
                    Custom ID (np. PlayNow tvod ID):
                    <input
                      type="text"
                      value={customId}
                      onChange={e => setCustomId(e.target.value)}
                      placeholder="np. 39166778"
                      style={inputStyle}
                    />
                  </label>

                  <label style={labelStyle}>
                    URL strony filmu (opcjonalne):
                    <input
                      type="text"
                      value={customUrl}
                      onChange={e => setCustomUrl(e.target.value)}
                      placeholder="np. https://playnow.pl/tvod/39166778"
                      style={inputStyle}
                    />
                  </label>

                  <label style={labelStyle}>
                    Gatunek (PL):
                    <input
                      type="text"
                      value={customGenre}
                      onChange={e => setCustomGenre(e.target.value)}
                      placeholder="np. Akcja"
                      style={inputStyle}
                    />
                  </label>

                  <label style={labelStyle}>
                    Cena (zł):
                    <input
                      type="number"
                      value={customPrice}
                      onChange={e => setCustomPrice(e.target.value)}
                      placeholder="19"
                      style={inputStyle}
                    />
                  </label>

                  <label style={labelStyle}>
                    Custom poster URL (opcjonalne — domyślnie z TMDB):
                    <input
                      type="text"
                      value={customPosterUrl}
                      onChange={e => setCustomPosterUrl(e.target.value)}
                      placeholder="https://r.playcdn.tv/..."
                      style={inputStyle}
                    />
                  </label>

                  <label style={labelStyle}>
                    Link do zwiastuna (DASH .smil / .mpd / MP4 / YouTube) — używany w sliderze Kino Play:
                    <input
                      type="text"
                      value={customTrailerUrl}
                      onChange={e => setCustomTrailerUrl(e.target.value)}
                      placeholder="np. https://n-1411-3.dcs.redcdn.pl/dash/.../dash.smil"
                      style={inputStyle}
                    />
                  </label>
                </div>

                {error && <div style={errorStyle}>{error}</div>}

                <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                  <button onClick={() => { setSelected(null); setDetails(null); }} style={secondaryBtn}>
                    ← Zmień film
                  </button>
                  <button onClick={handleSave} disabled={saving || !customId.trim()} style={primaryBtn}>
                    {saving ? 'Zapisuję…' : '💾 Zapisz do bazy'}
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

const overlayStyle = {
  position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999
};
const modalStyle = {
  background: '#1a1a1a', color: '#eee', borderRadius: 8, padding: 24,
  width: 'min(720px, 90vw)', maxHeight: '90vh', overflowY: 'auto'
};
const headerStyle = {
  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
  borderBottom: '1px solid #333', paddingBottom: 12, marginBottom: 12
};
const closeBtnStyle = {
  background: 'transparent', color: '#aaa', border: 'none', fontSize: 22, cursor: 'pointer'
};
const inputStyle = {
  flex: 1, padding: '8px 12px', borderRadius: 4, border: '1px solid #444',
  background: '#0d0d0d', color: '#eee', fontSize: 14, marginTop: 4
};
const primaryBtn = {
  padding: '8px 16px', background: '#3b82f6', color: '#fff', border: 'none',
  borderRadius: 4, cursor: 'pointer', fontSize: 14
};
const secondaryBtn = {
  padding: '8px 16px', background: '#444', color: '#fff', border: 'none',
  borderRadius: 4, cursor: 'pointer', fontSize: 14
};
const resultRowStyle = {
  display: 'flex', padding: 8, marginBottom: 4, borderRadius: 4,
  cursor: 'pointer', background: '#222', alignItems: 'flex-start'
};
const errorStyle = {
  padding: 8, background: '#5a1a1a', color: '#f88', borderRadius: 4, margin: '8px 0'
};
const labelStyle = {
  display: 'flex', flexDirection: 'column', marginBottom: 12, fontSize: 13, color: '#bbb'
};
