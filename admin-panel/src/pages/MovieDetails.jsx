import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { supabase } from '../supabase';

const TMDB_API_KEY = '716cc02044e4d92d0a012a426902cc2d';
const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

function MovieDetails() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [movie, setMovie] = useState(null);
  const [tmdbData, setTmdbData] = useState(null);
  const [tmdbImages, setTmdbImages] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tmdbLoading, setTmdbLoading] = useState(false);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  // Paginacja dla obrazów
  const [backdropPage, setBackdropPage] = useState(1);
  const [posterPage, setPosterPage] = useState(1);
  const [logoPage, setLogoPage] = useState(1);

  // Wyszukiwarka TMDB
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);

  // Modal "Dodaj do slidera"
  const [sliderModal, setSliderModal] = useState(false);
  const [slotNumber, setSlotNumber] = useState('');

  const ITEMS_PER_PAGE = {
    backdrops: 12,
    posters: 8,
    logos: 6
  };

  useEffect(() => {
    fetchMovie();
  }, [id]);

  const fetchMovie = async () => {
    const { data, error } = await supabase
      .from('movies')
      .select('*')
      .eq('id', id)
      .single();

    if (error) {
      setError('Movie not found');
    } else {
      setMovie(data);
    }
    setLoading(false);
  };

  const searchTMDB = async () => {
    if (!movie) return;

    setTmdbLoading(true);
    setError('');

    try {
      // Search for movie by title
      const searchUrl = `https://api.themoviedb.org/3/search/movie?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(movie.title)}&language=pl-PL`;
      const searchRes = await fetch(searchUrl);
      const searchData = await searchRes.json();

      if (searchData.results && searchData.results.length > 0) {
        const tmdbMovie = searchData.results[0];

        // Get full movie details (Polish)
        const detailsUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}?api_key=${TMDB_API_KEY}&language=pl-PL&append_to_response=credits`;
        const detailsRes = await fetch(detailsUrl);
        const detailsData = await detailsRes.json();

        // Get videos separately (without language filter - gets all videos including English trailers)
        const videosUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/videos?api_key=${TMDB_API_KEY}`;
        const videosRes = await fetch(videosUrl);
        const videosData = await videosRes.json();

        // Get all images (backdrops, posters, logos)
        const imagesUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/images?api_key=${TMDB_API_KEY}`;
        const imagesRes = await fetch(imagesUrl);
        const imagesData = await imagesRes.json();

        // Merge videos into detailsData
        detailsData.videos = videosData;

        setTmdbData(detailsData);
        setTmdbImages(imagesData);
      } else {
        setError('Nie znaleziono filmu w TMDB');
      }
    } catch (err) {
      setError('Błąd połączenia z TMDB: ' + err.message);
    }

    setTmdbLoading(false);
  };

  // Ręczne wyszukiwanie w TMDB
  const manualSearchTMDB = async () => {
    if (!searchQuery.trim()) return;

    setSearching(true);
    setSearchResults([]);

    try {
      const searchUrl = `https://api.themoviedb.org/3/search/movie?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(searchQuery)}&language=pl-PL`;
      const searchRes = await fetch(searchUrl);
      const searchData = await searchRes.json();

      setSearchResults(searchData.results || []);
    } catch (err) {
      setError('Błąd wyszukiwania: ' + err.message);
    }

    setSearching(false);
  };

  // Wybierz film z wyników wyszukiwania
  const selectTmdbMovie = async (tmdbMovie) => {
    setTmdbLoading(true);
    setSearchResults([]);
    setSearchQuery('');

    try {
      // Get full movie details (Polish)
      const detailsUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}?api_key=${TMDB_API_KEY}&language=pl-PL&append_to_response=credits`;
      const detailsRes = await fetch(detailsUrl);
      const detailsData = await detailsRes.json();

      // Get videos separately (without language filter)
      const videosUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/videos?api_key=${TMDB_API_KEY}`;
      const videosRes = await fetch(videosUrl);
      const videosData = await videosRes.json();

      // Get all images
      const imagesUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/images?api_key=${TMDB_API_KEY}`;
      const imagesRes = await fetch(imagesUrl);
      const imagesData = await imagesRes.json();

      detailsData.videos = videosData;

      setTmdbData(detailsData);
      setTmdbImages(imagesData);
    } catch (err) {
      setError('Błąd pobierania danych: ' + err.message);
    }

    setTmdbLoading(false);
  };

  const saveBackdropToSupabase = async (backdropPath) => {
    setSaving(true);
    const backdropUrl = `${TMDB_IMAGE_BASE}/original${backdropPath}`;

    const { error } = await supabase
      .from('movies')
      .update({ backdrop_url: backdropUrl })
      .eq('id', id);

    if (error) {
      setMessage('Błąd zapisu: ' + error.message);
    } else {
      setMessage('Backdrop zapisany!');
      setMovie({ ...movie, backdrop_url: backdropUrl });
    }
    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  };

  const saveTmdbDataToSupabase = async () => {
    if (!tmdbData) return;

    setSaving(true);

    // Extract directors, writers, producers from crew
    const directors = tmdbData.credits?.crew
      ?.filter(p => p.job === 'Director')
      .map(p => ({ name: p.name, id: p.id })) || [];

    const writers = tmdbData.credits?.crew
      ?.filter(p => ['Writer', 'Screenplay', 'Story'].includes(p.job))
      .map(p => ({ name: p.name, job: p.job, id: p.id })) || [];

    const producers = tmdbData.credits?.crew
      ?.filter(p => p.job === 'Producer')
      .slice(0, 5)
      .map(p => ({ name: p.name, id: p.id })) || [];

    // Extract cast (top 15)
    const cast = tmdbData.credits?.cast
      ?.slice(0, 15)
      .map(a => ({
        name: a.name,
        character: a.character,
        profile_path: a.profile_path,
        id: a.id,
        order: a.order
      })) || [];

    // Extract images
    const backdrops = tmdbImages?.backdrops
      ?.slice(0, 10)
      .map(b => ({ file_path: b.file_path, width: b.width, height: b.height })) || [];

    const posters = tmdbImages?.posters
      ?.slice(0, 5)
      .map(p => ({ file_path: p.file_path, width: p.width, height: p.height })) || [];

    const logos = tmdbImages?.logos
      ?.slice(0, 3)
      .map(l => ({ file_path: l.file_path, width: l.width, height: l.height })) || [];

    // Extract videos
    const videos = tmdbData.videos?.results
      ?.slice(0, 5)
      .map(v => ({ key: v.key, name: v.name, type: v.type, site: v.site })) || [];

    const updateData = {
      // Basic info
      tmdb_id: tmdbData.id,
      tmdb_title: tmdbData.title,
      tmdb_original_title: tmdbData.original_title,
      tmdb_overview: tmdbData.overview,
      tmdb_tagline: tmdbData.tagline,
      tmdb_release_date: tmdbData.release_date,
      tmdb_runtime: tmdbData.runtime,
      tmdb_vote_average: tmdbData.vote_average,
      tmdb_vote_count: tmdbData.vote_count,
      tmdb_popularity: tmdbData.popularity,

      // Images URLs
      backdrop_url: tmdbData.backdrop_path ? `${TMDB_IMAGE_BASE}/original${tmdbData.backdrop_path}` : null,
      tmdb_poster_url: tmdbData.poster_path ? `${TMDB_IMAGE_BASE}/original${tmdbData.poster_path}` : null,
      tmdb_logo_url: logos[0]?.file_path ? `${TMDB_IMAGE_BASE}/original${logos[0].file_path}` : null,

      // Genres
      tmdb_genres: tmdbData.genres || [],

      // Crew & Cast (as JSONB)
      tmdb_directors: directors,
      tmdb_writers: writers,
      tmdb_producers: producers,
      tmdb_cast: cast,

      // All images (as JSONB)
      tmdb_backdrops: backdrops,
      tmdb_posters: posters,
      tmdb_logos: logos,

      // Videos
      tmdb_videos: videos,

      // Additional info
      tmdb_budget: tmdbData.budget,
      tmdb_revenue: tmdbData.revenue,
      tmdb_status: tmdbData.status,
      tmdb_imdb_id: tmdbData.imdb_id,

      // Sync timestamp
      tmdb_synced_at: new Date().toISOString(),
    };

    const { error } = await supabase
      .from('movies')
      .update(updateData)
      .eq('id', id);

    if (error) {
      setMessage('Błąd zapisu: ' + error.message);
    } else {
      setMessage('✅ Wszystkie dane TMDB zapisane!');
      setMovie({ ...movie, ...updateData });
    }
    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  };

  // Zapisz pojedyncze pole do bazy
  const saveFieldToSupabase = async (fieldName, value, displayName) => {
    setSaving(true);
    const { error } = await supabase
      .from('movies')
      .update({ [fieldName]: value })
      .eq('id', id);

    if (error) {
      setMessage(`Błąd zapisu ${displayName}: ` + error.message);
    } else {
      setMessage(`✅ ${displayName} zapisane!`);
      setMovie({ ...movie, [fieldName]: value });
    }
    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  };

  // Zapisz opis z TMDB
  const saveOverview = () => {
    if (tmdbData?.overview) {
      saveFieldToSupabase('short_description', tmdbData.overview, 'Opis');
    }
  };

  // Zapisz gatunki z TMDB
  const saveGenres = () => {
    if (tmdbData?.genres) {
      const genreNames = tmdbData.genres.map(g => g.name).join(', ');
      saveFieldToSupabase('genre', genreNames, 'Gatunki');
    }
  };

  // Zapisz trailer
  const saveTrailer = (videoKey) => {
    saveFieldToSupabase('youtube_url', `https://www.youtube.com/watch?v=${videoKey}`, 'Trailer');
  };

  // Zapisz logo do tmdb_logo_url
  const saveLogoToSupabase = async (logoPath) => {
    const logoUrl = `${TMDB_IMAGE_BASE}/original${logoPath}`;
    saveFieldToSupabase('tmdb_logo_url', logoUrl, 'Logo');
  };

  // Zapisz logo do selected_logo_url (dla slidera - wyświetlane zamiast tytułu)
  const saveSelectedLogoToSupabase = async (logoPath) => {
    setSaving(true);
    const logoUrl = logoPath ? `${TMDB_IMAGE_BASE}/original${logoPath}` : null;

    const { error } = await supabase
      .from('movies')
      .update({ selected_logo_url: logoUrl })
      .eq('id', id);

    if (error) {
      setMessage('Błąd zapisu logo: ' + error.message);
    } else {
      setMessage(logoUrl ? 'Logo dla slidera zapisane! Będzie wyświetlane zamiast tytułu.' : 'Logo dla slidera usunięte.');
      setMovie({ ...movie, selected_logo_url: logoUrl });
    }
    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  };

  // Dodaj film do slidera
  const addToSlider = async () => {
    const slot = parseInt(slotNumber);
    if (!slot || slot < 1 || slot > 10) {
      setMessage('Błąd: Podaj numer slotu 1-10');
      setTimeout(() => setMessage(''), 3000);
      return;
    }

    setSaving(true);

    // Parsuj runtime z własnej bazy (np. "124 min" -> 124)
    let runtimeMinutes = movie.tmdb_runtime;
    if (movie.runtime && typeof movie.runtime === 'string') {
      const match = movie.runtime.match(/(\d+)/);
      if (match) runtimeMinutes = parseInt(match[1]);
    }

    // Konwertuj genre z własnej bazy na tablicę
    let genresArray = movie.tmdb_genres || [];
    if (movie.genre && typeof movie.genre === 'string') {
      genresArray = movie.genre.split(',').map(g => ({ name: g.trim() }));
    }

    const sliderData = {
      slot_position: slot,
      movie_id: movie.id,
      title: movie.title,
      backdrop_url: movie.backdrop_url,
      poster_url: movie.poster_url || movie.tmdb_poster_url,
      logo_url: movie.tmdb_logo_url,
      short_description: movie.short_description,
      tmdb_overview: movie.tmdb_overview,
      release_year: movie.tmdb_release_date?.substring(0, 4),
      runtime: runtimeMinutes,
      vote_average: movie.tmdb_vote_average,
      genres: genresArray,
      tmdb_id: movie.tmdb_id,
      // Własne dane z bazy movies
      own_genre: movie.genre,
      own_runtime: movie.runtime,
    };

    // Upsert - update or insert
    const { error } = await supabase
      .from('slider_movies')
      .upsert(sliderData, { onConflict: 'slot_position' });

    if (error) {
      setMessage('Błąd dodawania do slidera: ' + error.message);
    } else {
      setMessage(`✅ Film dodany do slotu ${slot}!`);
      setSliderModal(false);
      setSlotNumber('');
    }
    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  };

  if (loading) {
    return <div className="loading">Loading movie...</div>;
  }

  if (error && !movie) {
    return <div className="error-page">{error}</div>;
  }

  return (
    <div className="movie-details-page">
      <button onClick={() => navigate('/movies')} className="back-button">
        ← Powrót do listy
      </button>

      <h1>{movie?.title}</h1>

      {message && (
        <div className={`message ${message.includes('Błąd') ? 'error' : 'success'}`}>
          {message}
        </div>
      )}

      <div className="details-grid">
        {/* SUPABASE DATA */}
        <div className="data-section supabase-data">
          <h2>📦 Dane z Supabase</h2>
          <div className="data-card">
            <div className="poster-container">
              <img
                src={movie?.poster_url}
                alt={movie?.title}
                className="poster-image"
                onError={(e) => e.target.src = 'https://via.placeholder.com/200x300?text=No+Poster'}
              />
            </div>
            <div className="data-fields">
              <div className="field">
                <label>ID:</label>
                <span>{movie?.id}</span>
              </div>
              <div className="field">
                <label>Tytuł:</label>
                <span>{movie?.title}</span>
              </div>
              <div className="field">
                <label>Gatunek:</label>
                <span>{movie?.genre}</span>
              </div>
              <div className="field">
                <label>Czas trwania:</label>
                <span>{movie?.runtime}</span>
              </div>
              <div className="field">
                <label>Cena:</label>
                <span>{movie?.price} zł</span>
              </div>
              <div className="field">
                <label>URL:</label>
                <a href={movie?.url} target="_blank" rel="noreferrer">{movie?.url}</a>
              </div>
              {movie?.backdrop_url && (
                <div className="field">
                  <label>Backdrop URL:</label>
                  <span className="truncate">{movie?.backdrop_url}</span>
                </div>
              )}
              {movie?.tmdb_id && (
                <div className="field">
                  <label>TMDB ID:</label>
                  <span style={{ color: '#5AECD3' }}>{movie.tmdb_id}</span>
                </div>
              )}
              {movie?.youtube_url && (
                <div className="field">
                  <label>🎬 Trailer:</label>
                  <a href={movie.youtube_url} target="_blank" rel="noreferrer" style={{ color: '#ff6b6b' }}>
                    {movie.youtube_url}
                  </a>
                </div>
              )}
              {movie?.slider_glow_color && (
                <div className="field">
                  <label>🎨 Glow Color:</label>
                  <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <div style={{
                      width: '20px',
                      height: '20px',
                      borderRadius: '4px',
                      background: movie.slider_glow_color,
                      border: '1px solid #fff'
                    }} />
                    {movie.slider_glow_color}
                  </span>
                </div>
              )}
              {movie?.is_slider && (
                <div className="field">
                  <label>📺 Slider:</label>
                  <span style={{ color: '#5AECD3' }}>Slot {movie.slider_order}</span>
                </div>
              )}
              {movie?.is_top10 && (
                <div className="field">
                  <label>🏆 Top 10:</label>
                  <span style={{ color: '#ffd700' }}>Pozycja #{movie.top10_order}</span>
                </div>
              )}
              {movie?.short_description && (
                <div className="field full-width short-desc-field">
                  <label>📝 Krótki opis (z bazy):</label>
                  <p className="short-description">{movie.short_description}</p>
                </div>
              )}
            </div>
          </div>

          {movie?.backdrop_url && (
            <div className="backdrop-preview">
              <h3>Obecny Backdrop</h3>
              <img src={movie.backdrop_url} alt="Current backdrop" />
            </div>
          )}

          {/* Selected Logo for Slider Display */}
          {movie?.selected_logo_url && (
            <div className="selected-logo-preview">
              <h3>🎬 Logo dla slidera (wyświetlane zamiast tytułu)</h3>
              <div className="selected-logo-container">
                <img
                  src={movie.selected_logo_url}
                  alt="Selected Logo for Slider"
                  className="selected-logo-img"
                />
                <button
                  className="remove-logo-btn"
                  onClick={() => saveSelectedLogoToSupabase(null)}
                  disabled={saving}
                >
                  ❌ Usuń logo (pokaż tytuł tekstowy)
                </button>
              </div>
            </div>
          )}
        </div>

        {/* TMDB DATA */}
        <div className="data-section tmdb-data">
          <h2>🎬 Dane z TMDB</h2>

          {!tmdbData ? (
            <div className="fetch-tmdb">
              {/* Auto search button */}
              <button
                onClick={searchTMDB}
                disabled={tmdbLoading}
                className="tmdb-button"
              >
                {tmdbLoading ? 'Szukam...' : '🔍 Automatycznie pobierz dane z TMDB'}
              </button>

              {/* Manual search */}
              <div className="manual-search">
                <p className="search-hint">Nie znaleziono? Wyszukaj ręcznie:</p>
                <div className="search-input-group">
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    onKeyPress={(e) => e.key === 'Enter' && manualSearchTMDB()}
                    placeholder="Wpisz tytuł filmu..."
                    className="tmdb-search-input"
                  />
                  <button
                    onClick={manualSearchTMDB}
                    disabled={searching || !searchQuery.trim()}
                    className="search-btn"
                  >
                    {searching ? '...' : '🔍'}
                  </button>
                </div>

                {/* Search results */}
                {searchResults.length > 0 && (
                  <div className="search-results">
                    <h4>Wyniki wyszukiwania ({searchResults.length}):</h4>
                    <div className="results-grid">
                      {searchResults.map((result) => (
                        <div
                          key={result.id}
                          className="search-result-item"
                          onClick={() => selectTmdbMovie(result)}
                        >
                          {result.poster_path ? (
                            <img
                              src={`${TMDB_IMAGE_BASE}/w92${result.poster_path}`}
                              alt={result.title}
                            />
                          ) : (
                            <div className="no-poster">🎬</div>
                          )}
                          <div className="result-info">
                            <span className="result-title">{result.title}</span>
                            <span className="result-year">
                              {result.release_date?.substring(0, 4) || '?'}
                            </span>
                            <span className="result-rating">⭐ {result.vote_average?.toFixed(1)}</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {error && <p className="error-text">{error}</p>}
            </div>
          ) : (
            <>
              <div className="data-card">
                <div className="poster-container">
                  {tmdbData.poster_path && (
                    <img
                      src={`${TMDB_IMAGE_BASE}/w300${tmdbData.poster_path}`}
                      alt={tmdbData.title}
                      className="poster-image"
                    />
                  )}
                </div>
                <div className="data-fields">
                  <div className="field">
                    <label>TMDB ID:</label>
                    <span>{tmdbData.id}</span>
                  </div>
                  <div className="field">
                    <label>Tytuł:</label>
                    <span>{tmdbData.title}</span>
                  </div>
                  <div className="field">
                    <label>Oryginalny tytuł:</label>
                    <span>{tmdbData.original_title}</span>
                  </div>
                  <div className="field">
                    <label>Data premiery:</label>
                    <span>{tmdbData.release_date}</span>
                  </div>
                  <div className="field">
                    <label>Ocena:</label>
                    <span>⭐ {tmdbData.vote_average?.toFixed(1)} ({tmdbData.vote_count} głosów)</span>
                  </div>
                  <div className="field">
                    <label>Czas trwania:</label>
                    <span>{tmdbData.runtime} min</span>
                  </div>
                  <div className="field field-with-action">
                    <label>Gatunki:</label>
                    <span>{tmdbData.genres?.map(g => g.name).join(', ')}</span>
                    <button
                      onClick={saveGenres}
                      disabled={saving}
                      className="inline-action-btn"
                      title="Zapisz gatunki do naszej bazy"
                    >
                      💾 Zapisz
                    </button>
                  </div>
                  <div className="field full-width field-with-action">
                    <label>Opis:</label>
                    <p className="overview">{tmdbData.overview}</p>
                    <button
                      onClick={saveOverview}
                      disabled={saving}
                      className="inline-action-btn"
                      title="Użyj jako short_description w naszej bazie"
                    >
                      💾 Użyj jako opis
                    </button>
                  </div>
                </div>
              </div>

              <div className="action-buttons-row">
                <button
                  onClick={saveTmdbDataToSupabase}
                  disabled={saving}
                  className="save-tmdb-button"
                >
                  {saving ? 'Zapisuję...' : '💾 Zapisz dane TMDB do Supabase'}
                </button>

                <button
                  onClick={() => setSliderModal(true)}
                  disabled={saving}
                  className="add-to-slider-button"
                >
                  🎬 Dodaj do slidera
                </button>
              </div>

              {/* DIRECTORS & CREW */}
              {tmdbData.credits?.crew && (
                <div className="images-section">
                  <h3>🎬 REŻYSERIA I EKIPA</h3>
                  <div className="crew-list">
                    {tmdbData.credits.crew
                      .filter(person => ['Director', 'Writer', 'Screenplay', 'Producer'].includes(person.job))
                      .slice(0, 10)
                      .map((person, idx) => (
                        <div key={idx} className="crew-item">
                          <span className="crew-job">{person.job === 'Director' ? '🎬 Reżyser' :
                            person.job === 'Writer' ? '✍️ Scenarzysta' :
                            person.job === 'Screenplay' ? '📝 Scenariusz' : '🎞️ Producent'}</span>
                          <span className="crew-name">{person.name}</span>
                        </div>
                      ))}
                  </div>
                </div>
              )}

              {/* CAST */}
              {tmdbData.credits?.cast && tmdbData.credits.cast.length > 0 && (
                <div className="images-section">
                  <h3>🎭 OBSADA ({tmdbData.credits.cast.length})</h3>
                  <div className="cast-grid">
                    {tmdbData.credits.cast.slice(0, 12).map((actor, idx) => (
                      <div key={idx} className="cast-item">
                        {actor.profile_path ? (
                          <img
                            src={`${TMDB_IMAGE_BASE}/w185${actor.profile_path}`}
                            alt={actor.name}
                            className="cast-photo"
                          />
                        ) : (
                          <div className="cast-photo-placeholder">👤</div>
                        )}
                        <div className="cast-info">
                          <span className="cast-name">{actor.name}</span>
                          <span className="cast-character">{actor.character}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* BACKDROPS */}
              {tmdbImages?.backdrops && tmdbImages.backdrops.length > 0 && (
                <div className="images-section">
                  <h3>🖼️ BACKDROPS ({tmdbImages.backdrops.length})</h3>
                  <p className="hint">Kliknij na backdrop, aby zapisać go do Supabase</p>
                  <div className="backdrops-grid">
                    {tmdbImages.backdrops.slice(0, backdropPage * ITEMS_PER_PAGE.backdrops).map((backdrop, idx) => (
                      <div
                        key={idx}
                        className="backdrop-item"
                        onClick={() => saveBackdropToSupabase(backdrop.file_path)}
                      >
                        <img
                          src={`${TMDB_IMAGE_BASE}/w780${backdrop.file_path}`}
                          alt={`Backdrop ${idx + 1}`}
                        />
                        <div className="backdrop-info">
                          <span>{backdrop.width}x{backdrop.height}</span>
                          <button className="select-btn">Wybierz</button>
                        </div>
                      </div>
                    ))}
                  </div>
                  {tmdbImages.backdrops.length > backdropPage * ITEMS_PER_PAGE.backdrops && (
                    <button onClick={() => setBackdropPage(p => p + 1)} className="load-more-btn">
                      Pokaż więcej ({tmdbImages.backdrops.length - backdropPage * ITEMS_PER_PAGE.backdrops} pozostało)
                    </button>
                  )}
                </div>
              )}

              {/* POSTERS */}
              {tmdbImages?.posters && tmdbImages.posters.length > 0 && (
                <div className="images-section">
                  <h3>🎬 POSTERY ({tmdbImages.posters.length})</h3>
                  <div className="posters-grid">
                    {tmdbImages.posters.slice(0, posterPage * ITEMS_PER_PAGE.posters).map((poster, idx) => (
                      <div key={idx} className="poster-item">
                        <img
                          src={`${TMDB_IMAGE_BASE}/w300${poster.file_path}`}
                          alt={`Poster ${idx + 1}`}
                        />
                        <span>{poster.width}x{poster.height}</span>
                      </div>
                    ))}
                  </div>
                  {tmdbImages.posters.length > posterPage * ITEMS_PER_PAGE.posters && (
                    <button onClick={() => setPosterPage(p => p + 1)} className="load-more-btn">
                      Pokaż więcej ({tmdbImages.posters.length - posterPage * ITEMS_PER_PAGE.posters} pozostało)
                    </button>
                  )}
                </div>
              )}

              {/* LOGOS */}
              {tmdbImages?.logos && tmdbImages.logos.length > 0 && (
                <div className="images-section">
                  <h3>✨ LOGA ({tmdbImages.logos.length})</h3>
                  <p className="hint">Kliknij "📺 Dla slidera", aby wyświetlać logo zamiast tytułu na sliderze</p>
                  <div className="logos-grid">
                    {tmdbImages.logos.slice(0, logoPage * ITEMS_PER_PAGE.logos).map((logo, idx) => {
                      const fullLogoUrl = `${TMDB_IMAGE_BASE}/original${logo.file_path}`;
                      const isSelectedForSlider = movie.selected_logo_url === fullLogoUrl;
                      return (
                        <div
                          key={idx}
                          className={`logo-item ${isSelectedForSlider ? 'selected-for-slider' : ''}`}
                        >
                          <img
                            src={`${TMDB_IMAGE_BASE}/w300${logo.file_path}`}
                            alt={`Logo ${idx + 1}`}
                          />
                          <div className="logo-info">
                            <span>{logo.width}x{logo.height}</span>
                          </div>
                          <div className="logo-buttons">
                            <button
                              onClick={() => saveLogoToSupabase(logo.file_path)}
                              disabled={saving}
                              className="select-btn"
                            >
                              💾 Zapisz
                            </button>
                            <button
                              onClick={() => saveSelectedLogoToSupabase(logo.file_path)}
                              disabled={saving}
                              className={`select-btn slider-btn ${isSelectedForSlider ? 'selected' : ''}`}
                            >
                              {isSelectedForSlider ? '✅ Wybrane' : '📺 Dla slidera'}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                  {tmdbImages.logos.length > logoPage * ITEMS_PER_PAGE.logos && (
                    <button onClick={() => setLogoPage(p => p + 1)} className="load-more-btn">
                      Pokaż więcej ({tmdbImages.logos.length - logoPage * ITEMS_PER_PAGE.logos} pozostało)
                    </button>
                  )}
                </div>
              )}

              {/* VIDEOS/TRAILERS */}
              {tmdbData.videos?.results && tmdbData.videos.results.length > 0 && (
                <div className="images-section">
                  <h3>🎥 TRAILERY I WIDEO ({tmdbData.videos.results.length})</h3>
                  <p className="hint">Kliknij "Zapisz trailer", aby ustawić go jako główny trailer filmu</p>
                  <div className="videos-grid">
                    {tmdbData.videos.results.map((video, idx) => (
                      <div key={idx} className="video-item-wrapper">
                        <a
                          href={`https://www.youtube.com/watch?v=${video.key}`}
                          target="_blank"
                          rel="noreferrer"
                          className="video-item"
                        >
                          <div className="video-thumbnail">
                            <img
                              src={`https://img.youtube.com/vi/${video.key}/mqdefault.jpg`}
                              alt={video.name}
                            />
                            <div className="play-icon">▶</div>
                          </div>
                          <div className="video-info">
                            <span className="video-name">{video.name}</span>
                            <span className="video-type">{video.type}</span>
                          </div>
                        </a>
                        <button
                          onClick={(e) => {
                            e.preventDefault();
                            saveTrailer(video.key);
                          }}
                          disabled={saving}
                          className="save-trailer-btn"
                        >
                          💾 Zapisz trailer
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* MODAL DODAJ DO SLIDERA */}
      {sliderModal && (
        <div className="slider-modal-overlay" onClick={() => setSliderModal(false)}>
          <div className="slider-modal" onClick={(e) => e.stopPropagation()}>
            <h2>🎬 Dodaj do slidera</h2>

            {/* Podgląd slajdu */}
            <div className="slider-preview">
              {movie?.backdrop_url ? (
                <img
                  src={movie.backdrop_url}
                  alt={movie.title}
                  className="slider-preview-backdrop"
                />
              ) : (
                <div className="slider-preview-placeholder">
                  Brak backdrop - wybierz go z TMDB
                </div>
              )}
              <div className="slider-preview-content">
                <h3 className="slider-preview-title">{movie?.title}</h3>
                <div className="slider-preview-meta">
                  {movie?.tmdb_release_date && (
                    <span>{movie.tmdb_release_date.substring(0, 4)}</span>
                  )}
                  {movie?.tmdb_runtime && (
                    <span>{movie.tmdb_runtime} min</span>
                  )}
                  {movie?.tmdb_vote_average && (
                    <span>⭐ {movie.tmdb_vote_average.toFixed(1)}</span>
                  )}
                  {movie?.genre && (
                    <span>{movie.genre}</span>
                  )}
                </div>
                <p className="slider-preview-description">
                  {movie?.short_description || movie?.tmdb_overview || 'Brak opisu'}
                </p>
              </div>
            </div>

            {/* Wybór numeru slotu */}
            <div className="slot-selector">
              <label>Numer slotu (1-10):</label>
              <input
                type="number"
                min="1"
                max="10"
                value={slotNumber}
                onChange={(e) => setSlotNumber(e.target.value)}
                placeholder="np. 1"
                className="slot-input"
              />
            </div>

            {/* Przyciski akcji */}
            <div className="slider-modal-actions">
              <button
                onClick={() => setSliderModal(false)}
                className="cancel-btn"
              >
                Anuluj
              </button>
              <button
                onClick={addToSlider}
                disabled={saving || !slotNumber}
                className="confirm-btn"
              >
                {saving ? 'Zapisuję...' : '✅ Dodaj do slotu'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default MovieDetails;
