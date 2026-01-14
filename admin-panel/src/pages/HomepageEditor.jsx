import React, { useState, useEffect, useCallback } from 'react';
import { supabase } from '../supabase';

const SECTION_TYPES = [
  { value: 'slider', label: '🎬 Hero Slider' },
  { value: 'horizontal', label: '📺 Poziomy rząd' },
  { value: 'top10', label: '🏆 Top 10' },
  { value: 'vertical', label: '📱 Pionowe karty' },
];

const SOURCE_TYPES = [
  { value: 'manual', label: '✋ Ręczny wybór', desc: 'Wybierz pojedyncze filmy' },
  { value: 'category', label: '📁 Kategoria', desc: 'Automatycznie z gatunku' },
  { value: 'newest', label: '🆕 Najnowsze', desc: 'Ostatnio dodane filmy' },
  { value: 'top_rated', label: '⭐ Najlepiej oceniane', desc: 'Wg ocen TMDB' },
];

function HomepageEditor() {
  const [sections, setSections] = useState([]);
  const [movies, setMovies] = useState([]);
  const [genres, setGenres] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  
  // Modal states
  const [showAddSection, setShowAddSection] = useState(false);
  const [showMoviePicker, setShowMoviePicker] = useState(false);
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);
  const [editingSection, setEditingSection] = useState(null);
  const [selectedSectionId, setSelectedSectionId] = useState(null);
  
  // New section form
  const [newSectionName, setNewSectionName] = useState('');
  const [newSectionType, setNewSectionType] = useState('horizontal');
  const [newSourceType, setNewSourceType] = useState('manual');
  const [newCategoryFilter, setNewCategoryFilter] = useState('');
  
  // Movie search
  const [movieSearch, setMovieSearch] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  
  // Category movies preview
  const [categoryMovies, setCategoryMovies] = useState({});

  // Fetch sections and their movies
  const fetchSections = useCallback(async () => {
    setLoading(true);
    
    try {
      // Try to fetch from Supabase
      const { data: sectionsData, error: sectionsError } = await supabase
        .from('homepage_sections')
        .select('*')
        .order('display_order', { ascending: true });

      if (sectionsError) {
        // Table doesn't exist, use localStorage
        console.log('Using localStorage fallback');
        const stored = localStorage.getItem('homepage_sections');
        if (stored) {
          const parsedSections = JSON.parse(stored);
          setSections(parsedSections);
          // Load category movies for category-type sections
          loadCategoryMovies(parsedSections);
        } else {
          // Initialize with default sections
          const defaultSections = [
            { id: '1', name: 'Hero Slider', type: 'slider', source_type: 'manual', display_order: 0, is_active: true, movies: [] },
            { id: '2', name: 'Nowości', type: 'horizontal', source_type: 'newest', display_order: 1, is_active: true, movies: [] },
            { id: '3', name: 'Polecane', type: 'horizontal', source_type: 'manual', display_order: 2, is_active: true, movies: [] },
            { id: '4', name: 'Top 10', type: 'top10', source_type: 'manual', display_order: 3, is_active: true, movies: [] },
            { id: '5', name: 'Akcja', type: 'horizontal', source_type: 'category', category_filter: 'Akcja', display_order: 4, is_active: true, movies: [] },
            { id: '6', name: 'Komedie', type: 'horizontal', source_type: 'category', category_filter: 'Komedia', display_order: 5, is_active: true, movies: [] },
          ];
          setSections(defaultSections);
          localStorage.setItem('homepage_sections', JSON.stringify(defaultSections));
          loadCategoryMovies(defaultSections);
        }
      } else {
        // Fetch movies for each section
        const sectionsWithMovies = await Promise.all(
          sectionsData.map(async (section) => {
            const { data: sectionMovies } = await supabase
              .from('section_movies')
              .select('movie_id, display_order')
              .eq('section_id', section.id)
              .order('display_order', { ascending: true });
            
            if (sectionMovies && sectionMovies.length > 0) {
              const movieIds = sectionMovies.map(sm => sm.movie_id);
              const { data: moviesData } = await supabase
                .from('movies')
                .select('id, title, poster_url, genre, price')
                .in('id', movieIds);
              
              // Sort movies by display_order
              const sortedMovies = movieIds.map(id => 
                moviesData?.find(m => m.id === id)
              ).filter(Boolean);
              
              return { ...section, movies: sortedMovies };
            }
            return { ...section, movies: [] };
          })
        );
        setSections(sectionsWithMovies);
        loadCategoryMovies(sectionsWithMovies);
      }
    } catch (err) {
      console.error('Error fetching sections:', err);
    }
    
    setLoading(false);
  }, []);

  // Load movies for category-based sections
  const loadCategoryMovies = async (sectionsList) => {
    const categoryPromises = sectionsList
      .filter(s => s.source_type === 'category' && s.category_filter)
      .map(async (section) => {
        const { data } = await supabase
          .from('movies')
          .select('id, title, poster_url, genre, price, created_at')
          .eq('is_active', true)
          .ilike('genre', `%${section.category_filter}%`)
          .order('created_at', { ascending: false })
          .limit(20);
        
        return { sectionId: section.id, movies: data || [] };
      });
    
    // Also load newest
    const newestSections = sectionsList.filter(s => s.source_type === 'newest');
    if (newestSections.length > 0) {
      const { data: newestMovies } = await supabase
        .from('movies')
        .select('id, title, poster_url, genre, price, created_at')
        .eq('is_active', true)
        .order('created_at', { ascending: false })
        .limit(20);
      
      newestSections.forEach(section => {
        categoryPromises.push(Promise.resolve({ sectionId: section.id, movies: newestMovies || [] }));
      });
    }
    
    // Also load top rated
    const topRatedSections = sectionsList.filter(s => s.source_type === 'top_rated');
    if (topRatedSections.length > 0) {
      const { data: topRatedMovies } = await supabase
        .from('movies')
        .select('id, title, poster_url, genre, price, tmdb_vote_average')
        .eq('is_active', true)
        .not('tmdb_vote_average', 'is', null)
        .order('tmdb_vote_average', { ascending: false })
        .limit(20);
      
      topRatedSections.forEach(section => {
        categoryPromises.push(Promise.resolve({ sectionId: section.id, movies: topRatedMovies || [] }));
      });
    }
    
    const results = await Promise.all(categoryPromises);
    const newCategoryMovies = {};
    results.forEach(({ sectionId, movies }) => {
      newCategoryMovies[sectionId] = movies;
    });
    setCategoryMovies(newCategoryMovies);
  };

  // Fetch all movies for picker
  const fetchMovies = async () => {
    const { data } = await supabase
      .from('movies')
      .select('id, title, poster_url, genre, price')
      .eq('is_active', true)
      .order('created_at', { ascending: false });
    
    setMovies(data || []);
    
    // Extract unique genres
    const allGenres = new Set();
    (data || []).forEach(movie => {
      if (movie.genre) {
        // Split by comma and clean up
        movie.genre.split(',').forEach(g => {
          const cleaned = g.trim();
          if (cleaned) allGenres.add(cleaned);
        });
      }
    });
    setGenres(Array.from(allGenres).sort());
  };

  useEffect(() => {
    fetchSections();
    fetchMovies();
  }, [fetchSections]);

  // Search movies
  useEffect(() => {
    if (movieSearch.length >= 2) {
      const results = movies.filter(m => 
        m.title.toLowerCase().includes(movieSearch.toLowerCase())
      );
      setSearchResults(results.slice(0, 20));
    } else {
      setSearchResults([]);
    }
  }, [movieSearch, movies]);

  // Save sections to localStorage (fallback) or Supabase
  const saveSections = async (updatedSections) => {
    setSaving(true);
    
    try {
      // Try Supabase first
      const { error } = await supabase
        .from('homepage_sections')
        .select('id')
        .limit(1);
      
      if (error) {
        // Use localStorage
        localStorage.setItem('homepage_sections', JSON.stringify(updatedSections));
      } else {
        // Save to Supabase
        for (const section of updatedSections) {
          await supabase
            .from('homepage_sections')
            .upsert({
              id: section.id,
              name: section.name,
              type: section.type,
              source_type: section.source_type || 'manual',
              category_filter: section.category_filter || null,
              display_order: section.display_order,
              is_active: section.is_active,
            });
          
          // Update section_movies only for manual sections
          if (section.source_type === 'manual' && section.movies) {
            // Delete existing
            await supabase
              .from('section_movies')
              .delete()
              .eq('section_id', section.id);
            
            // Insert new
            if (section.movies.length > 0) {
              await supabase
                .from('section_movies')
                .insert(
                  section.movies.map((movie, index) => ({
                    section_id: section.id,
                    movie_id: movie.id,
                    display_order: index,
                  }))
                );
            }
          }
        }
      }
      
      setSections(updatedSections);
      loadCategoryMovies(updatedSections);
      setMessage('✅ Zapisano zmiany!');
      setTimeout(() => setMessage(''), 3000);
    } catch (err) {
      setMessage('❌ Błąd zapisu: ' + err.message);
    }
    
    setSaving(false);
  };

  // Add new section
  const addSection = () => {
    if (!newSectionName.trim()) return;
    
    const newSection = {
      id: Date.now().toString(),
      name: newSectionName,
      type: newSectionType,
      source_type: newSourceType,
      category_filter: newSourceType === 'category' ? newCategoryFilter : null,
      display_order: sections.length,
      is_active: true,
      movies: [],
    };
    
    const updated = [...sections, newSection];
    saveSections(updated);
    setNewSectionName('');
    setNewSectionType('horizontal');
    setNewSourceType('manual');
    setNewCategoryFilter('');
    setShowAddSection(false);
  };

  // Delete section
  const deleteSection = async (sectionId) => {
    if (!window.confirm('Czy na pewno usunąć tę sekcję?')) return;
    
    const updated = sections.filter(s => s.id !== sectionId);
    updated.forEach((s, i) => s.display_order = i);
    
    try {
      await supabase
        .from('homepage_sections')
        .delete()
        .eq('id', sectionId);
    } catch (err) {
      // Ignore if table doesn't exist
    }
    
    saveSections(updated);
  };

  // Toggle section active
  const toggleSection = (sectionId) => {
    const updated = sections.map(s => 
      s.id === sectionId ? { ...s, is_active: !s.is_active } : s
    );
    saveSections(updated);
  };

  // Move section up/down
  const moveSection = (sectionId, direction) => {
    const index = sections.findIndex(s => s.id === sectionId);
    if (
      (direction === 'up' && index === 0) ||
      (direction === 'down' && index === sections.length - 1)
    ) return;
    
    const newIndex = direction === 'up' ? index - 1 : index + 1;
    const updated = [...sections];
    [updated[index], updated[newIndex]] = [updated[newIndex], updated[index]];
    updated.forEach((s, i) => s.display_order = i);
    
    saveSections(updated);
  };

  // Add movie to section
  const addMovieToSection = (movie) => {
    const section = sections.find(s => s.id === selectedSectionId);
    if (!section) return;
    
    if (section.movies?.some(m => m.id === movie.id)) {
      setMessage('⚠️ Film już jest w tej sekcji');
      setTimeout(() => setMessage(''), 2000);
      return;
    }
    
    const updated = sections.map(s => {
      if (s.id === selectedSectionId) {
        return { ...s, movies: [...(s.movies || []), movie] };
      }
      return s;
    });
    
    saveSections(updated);
    setMessage(`✅ Dodano "${movie.title}"`);
    setTimeout(() => setMessage(''), 2000);
  };

  // Remove movie from section
  const removeMovieFromSection = (sectionId, movieId) => {
    const updated = sections.map(s => {
      if (s.id === sectionId) {
        return { ...s, movies: s.movies.filter(m => m.id !== movieId) };
      }
      return s;
    });
    saveSections(updated);
  };

  // Move movie within section
  const moveMovieInSection = (sectionId, movieId, direction) => {
    const section = sections.find(s => s.id === sectionId);
    if (!section) return;
    
    const movieIndex = section.movies.findIndex(m => m.id === movieId);
    if (
      (direction === 'left' && movieIndex === 0) ||
      (direction === 'right' && movieIndex === section.movies.length - 1)
    ) return;
    
    const newIndex = direction === 'left' ? movieIndex - 1 : movieIndex + 1;
    const newMovies = [...section.movies];
    [newMovies[movieIndex], newMovies[newIndex]] = [newMovies[newIndex], newMovies[movieIndex]];
    
    const updated = sections.map(s => 
      s.id === sectionId ? { ...s, movies: newMovies } : s
    );
    saveSections(updated);
  };

  // Update section name
  const updateSectionName = (sectionId, newName) => {
    const updated = sections.map(s => 
      s.id === sectionId ? { ...s, name: newName } : s
    );
    saveSections(updated);
    setEditingSection(null);
  };

  // Change section source type
  const changeSectionSource = (sectionId, sourceType, categoryFilter = null) => {
    const updated = sections.map(s => {
      if (s.id === sectionId) {
        return { 
          ...s, 
          source_type: sourceType, 
          category_filter: categoryFilter,
          movies: sourceType === 'manual' ? s.movies : []
        };
      }
      return s;
    });
    saveSections(updated);
    setShowCategoryPicker(false);
  };

  // Get movies to display for a section
  const getSectionMovies = (section) => {
    if (section.source_type === 'manual') {
      return section.movies || [];
    }
    return categoryMovies[section.id] || [];
  };

  // Get source label
  const getSourceLabel = (section) => {
    if (section.source_type === 'category' && section.category_filter) {
      return `📁 ${section.category_filter}`;
    }
    return SOURCE_TYPES.find(s => s.value === section.source_type)?.label || '✋ Ręczny';
  };

  if (loading) {
    return <div className="loading">Ładowanie edytora...</div>;
  }

  return (
    <div className="homepage-editor">
      <div className="editor-header">
        <h1>🏠 Edytor Strony Głównej</h1>
        <p className="editor-subtitle">Zarządzaj sekcjami i filmami wyświetlanymi na stronie głównej</p>
        
        <div className="editor-actions">
          <button 
            onClick={() => setShowAddSection(true)} 
            className="add-section-btn"
          >
            ➕ Dodaj sekcję
          </button>
          
          <button 
            onClick={() => { fetchSections(); fetchMovies(); }} 
            className="refresh-btn"
            disabled={saving}
          >
            🔄 Odśwież
          </button>
        </div>
      </div>

      {message && (
        <div className={`editor-message ${message.includes('❌') ? 'error' : message.includes('⚠️') ? 'warning' : 'success'}`}>
          {message}
        </div>
      )}

      <div className="sections-container">
        {sections.map((section, index) => (
          <div 
            key={section.id} 
            className={`section-card ${!section.is_active ? 'inactive' : ''}`}
          >
            <div className="section-header">
              <div className="section-order">
                <button 
                  onClick={() => moveSection(section.id, 'up')}
                  disabled={index === 0}
                  className="order-btn"
                >
                  ⬆️
                </button>
                <span className="order-number">{index + 1}</span>
                <button 
                  onClick={() => moveSection(section.id, 'down')}
                  disabled={index === sections.length - 1}
                  className="order-btn"
                >
                  ⬇️
                </button>
              </div>
              
              <div className="section-info">
                {editingSection === section.id ? (
                  <input
                    type="text"
                    defaultValue={section.name}
                    onBlur={(e) => updateSectionName(section.id, e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        updateSectionName(section.id, e.target.value);
                      }
                    }}
                    autoFocus
                    className="section-name-input"
                  />
                ) : (
                  <h3 
                    className="section-name" 
                    onClick={() => setEditingSection(section.id)}
                    title="Kliknij aby edytować"
                  >
                    {section.name}
                  </h3>
                )}
                <span className="section-type">
                  {SECTION_TYPES.find(t => t.value === section.type)?.label || section.type}
                </span>
                <span 
                  className={`source-badge ${section.source_type || 'manual'}`}
                  onClick={() => {
                    setSelectedSectionId(section.id);
                    setShowCategoryPicker(true);
                  }}
                  title="Kliknij aby zmienić źródło"
                >
                  {getSourceLabel(section)}
                </span>
                <span className="movie-count">
                  {getSectionMovies(section).length} filmów
                </span>
              </div>
              
              <div className="section-actions">
                {section.source_type === 'manual' && (
                  <button
                    onClick={() => {
                      setSelectedSectionId(section.id);
                      setShowMoviePicker(true);
                    }}
                    className="add-movies-btn"
                  >
                    🎬 Dodaj filmy
                  </button>
                )}
                <button
                  onClick={() => {
                    setSelectedSectionId(section.id);
                    setShowCategoryPicker(true);
                  }}
                  className="source-btn"
                >
                  📁 Źródło
                </button>
                <button
                  onClick={() => toggleSection(section.id)}
                  className={`toggle-btn ${section.is_active ? 'active' : ''}`}
                >
                  {section.is_active ? '✅' : '❌'}
                </button>
                <button
                  onClick={() => deleteSection(section.id)}
                  className="delete-btn"
                >
                  🗑️
                </button>
              </div>
            </div>
            
            <div className="section-movies">
              {getSectionMovies(section).length > 0 ? (
                <div className="movies-row">
                  {getSectionMovies(section).map((movie, movieIndex) => (
                    <div key={movie.id} className="movie-card">
                      <img 
                        src={movie.poster_url} 
                        alt={movie.title}
                        onError={(e) => {
                          e.target.src = 'https://via.placeholder.com/120x180?text=No+Image';
                        }}
                      />
                      {section.source_type !== 'manual' && (
                        <div className="auto-badge">AUTO</div>
                      )}
                      <div className="movie-overlay">
                        <span className="movie-title">{movie.title}</span>
                        {section.source_type === 'manual' && (
                          <div className="movie-actions">
                            <button
                              onClick={() => moveMovieInSection(section.id, movie.id, 'left')}
                              disabled={movieIndex === 0}
                            >
                              ◀️
                            </button>
                            <button
                              onClick={() => removeMovieFromSection(section.id, movie.id)}
                              className="remove-btn"
                            >
                              ❌
                            </button>
                            <button
                              onClick={() => moveMovieInSection(section.id, movie.id, 'right')}
                              disabled={movieIndex === getSectionMovies(section).length - 1}
                            >
                              ▶️
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty-section">
                  {section.source_type === 'manual' ? (
                    <>
                      <p>Brak filmów w tej sekcji</p>
                      <button
                        onClick={() => {
                          setSelectedSectionId(section.id);
                          setShowMoviePicker(true);
                        }}
                      >
                        Dodaj pierwszy film
                      </button>
                    </>
                  ) : (
                    <p>Brak filmów w kategorii "{section.category_filter || 'auto'}"</p>
                  )}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Add Section Modal */}
      {showAddSection && (
        <div className="modal-overlay" onClick={() => setShowAddSection(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>➕ Nowa sekcja</h2>
            
            <div className="form-group">
              <label>Nazwa sekcji</label>
              <input
                type="text"
                value={newSectionName}
                onChange={(e) => setNewSectionName(e.target.value)}
                placeholder="np. Horrory, Nowości, Dla dzieci..."
              />
            </div>
            
            <div className="form-group">
              <label>Typ wyświetlania</label>
              <select
                value={newSectionType}
                onChange={(e) => setNewSectionType(e.target.value)}
              >
                {SECTION_TYPES.map(type => (
                  <option key={type.value} value={type.value}>
                    {type.label}
                  </option>
                ))}
              </select>
            </div>
            
            <div className="form-group">
              <label>Źródło filmów</label>
              <div className="source-options">
                {SOURCE_TYPES.map(source => (
                  <div 
                    key={source.value}
                    className={`source-option ${newSourceType === source.value ? 'selected' : ''}`}
                    onClick={() => setNewSourceType(source.value)}
                  >
                    <span className="source-label">{source.label}</span>
                    <span className="source-desc">{source.desc}</span>
                  </div>
                ))}
              </div>
            </div>
            
            {newSourceType === 'category' && (
              <div className="form-group">
                <label>Wybierz kategorię</label>
                <select
                  value={newCategoryFilter}
                  onChange={(e) => setNewCategoryFilter(e.target.value)}
                >
                  <option value="">-- Wybierz gatunek --</option>
                  {genres.map(genre => (
                    <option key={genre} value={genre}>{genre}</option>
                  ))}
                </select>
              </div>
            )}
            
            <div className="modal-actions">
              <button onClick={() => setShowAddSection(false)} className="cancel-btn">
                Anuluj
              </button>
              <button 
                onClick={addSection} 
                className="confirm-btn"
                disabled={!newSectionName.trim() || (newSourceType === 'category' && !newCategoryFilter)}
              >
                Dodaj sekcję
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Category/Source Picker Modal */}
      {showCategoryPicker && (
        <div className="modal-overlay" onClick={() => setShowCategoryPicker(false)}>
          <div className="modal source-picker-modal" onClick={e => e.stopPropagation()}>
            <h2>📁 Wybierz źródło filmów</h2>
            <p className="picker-subtitle">
              Sekcja: <strong>{sections.find(s => s.id === selectedSectionId)?.name}</strong>
            </p>
            
            <div className="source-grid">
              <div 
                className="source-card"
                onClick={() => changeSectionSource(selectedSectionId, 'manual')}
              >
                <span className="source-icon">✋</span>
                <span className="source-title">Ręczny wybór</span>
                <span className="source-desc">Wybierz pojedyncze filmy</span>
              </div>
              
              <div 
                className="source-card"
                onClick={() => changeSectionSource(selectedSectionId, 'newest')}
              >
                <span className="source-icon">🆕</span>
                <span className="source-title">Najnowsze</span>
                <span className="source-desc">Ostatnio dodane filmy</span>
              </div>
              
              <div 
                className="source-card"
                onClick={() => changeSectionSource(selectedSectionId, 'top_rated')}
              >
                <span className="source-icon">⭐</span>
                <span className="source-title">Najlepiej oceniane</span>
                <span className="source-desc">Wg ocen TMDB</span>
              </div>
            </div>
            
            <h3 className="category-header">📁 Lub wybierz kategorię:</h3>
            <div className="category-grid">
              {genres.map(genre => (
                <button
                  key={genre}
                  className="category-btn"
                  onClick={() => changeSectionSource(selectedSectionId, 'category', genre)}
                >
                  {genre}
                </button>
              ))}
            </div>
            
            <div className="modal-actions">
              <button onClick={() => setShowCategoryPicker(false)} className="cancel-btn">
                Anuluj
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Movie Picker Modal */}
      {showMoviePicker && (
        <div className="modal-overlay" onClick={() => setShowMoviePicker(false)}>
          <div className="modal movie-picker-modal" onClick={e => e.stopPropagation()}>
            <h2>🎬 Wybierz filmy</h2>
            <p className="picker-subtitle">
              Dodajesz do: <strong>{sections.find(s => s.id === selectedSectionId)?.name}</strong>
            </p>
            
            <div className="search-box">
              <input
                type="text"
                value={movieSearch}
                onChange={(e) => setMovieSearch(e.target.value)}
                placeholder="Szukaj filmu po tytule..."
                autoFocus
              />
            </div>
            
            <div className="picker-results">
              {movieSearch.length < 2 ? (
                <p className="picker-hint">Wpisz co najmniej 2 znaki aby wyszukać...</p>
              ) : searchResults.length === 0 ? (
                <p className="picker-hint">Nie znaleziono filmów</p>
              ) : (
                <div className="picker-grid">
                  {searchResults.map(movie => {
                    const isInSection = sections
                      .find(s => s.id === selectedSectionId)
                      ?.movies?.some(m => m.id === movie.id);
                    
                    return (
                      <div 
                        key={movie.id} 
                        className={`picker-movie ${isInSection ? 'already-added' : ''}`}
                        onClick={() => !isInSection && addMovieToSection(movie)}
                      >
                        <img 
                          src={movie.poster_url} 
                          alt={movie.title}
                          onError={(e) => {
                            e.target.src = 'https://via.placeholder.com/80x120?text=No+Image';
                          }}
                        />
                        <div className="picker-movie-info">
                          <span className="picker-movie-title">{movie.title}</span>
                          <span className="picker-movie-genre">{movie.genre}</span>
                          {isInSection && <span className="already-badge">✓ Dodany</span>}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
            
            <div className="modal-actions">
              <button onClick={() => {
                setShowMoviePicker(false);
                setMovieSearch('');
              }} className="confirm-btn">
                Gotowe
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default HomepageEditor;
