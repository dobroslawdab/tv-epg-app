import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabase } from '../supabase';

const ITEMS_PER_PAGE = 50;

function Movies() {
  const navigate = useNavigate();
  const [movies, setMovies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [genreFilter, setGenreFilter] = useState('');
  const [genres, setGenres] = useState([]);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({});

  // Bulk selection state
  const [selectedMovies, setSelectedMovies] = useState(new Set());
  const [bulkActionMessage, setBulkActionMessage] = useState('');

  // Top 10 filter
  const [showTop10Only, setShowTop10Only] = useState(false);
  const [top10Count, setTop10Count] = useState(0);

  // Slider filter
  const [showSliderOnly, setShowSliderOnly] = useState(false);
  const [sliderCount, setSliderCount] = useState(0);

  // Sort order
  const [sortOrder, setSortOrder] = useState('newest'); // 'newest', 'oldest', 'display_order'

  const fetchMovies = useCallback(async () => {
    setLoading(true);

    let query = supabase
      .from('movies')
      .select('*', { count: 'exact' });

    // If showing Top 10 only, order by top10_order
    if (showTop10Only) {
      query = query.eq('is_top10', true).order('top10_order', { ascending: true });
    } else if (showSliderOnly) {
      query = query.eq('is_slider', true).order('slider_order', { ascending: true });
    } else {
      // Apply sort order
      if (sortOrder === 'newest') {
        query = query.order('created_at', { ascending: false, nullsFirst: false });
      } else if (sortOrder === 'oldest') {
        query = query.order('created_at', { ascending: true, nullsFirst: true });
      } else {
        query = query.order('display_order', { ascending: true });
      }
      query = query.range(page * ITEMS_PER_PAGE, (page + 1) * ITEMS_PER_PAGE - 1);
    }

    if (searchTerm) {
      query = query.ilike('title', `%${searchTerm}%`);
    }

    if (genreFilter) {
      query = query.eq('genre', genreFilter);
    }

    const { data, error, count } = await query;

    if (error) {
      console.error('Error fetching movies:', error);
    } else {
      setMovies(data || []);
      setTotalCount(count || 0);
    }

    setLoading(false);
  }, [page, searchTerm, genreFilter, showTop10Only, showSliderOnly, sortOrder]);

  const fetchTop10Count = async () => {
    const { count } = await supabase
      .from('movies')
      .select('*', { count: 'exact', head: true })
      .eq('is_top10', true);
    setTop10Count(count || 0);
  };

  const fetchSliderCount = async () => {
    const { count } = await supabase
      .from('movies')
      .select('*', { count: 'exact', head: true })
      .eq('is_slider', true);
    setSliderCount(count || 0);
  };

  const fetchGenres = async () => {
    const { data } = await supabase
      .from('movies')
      .select('genre')
      .not('genre', 'is', null);

    const uniqueGenres = [...new Set(data?.map((m) => m.genre).filter(Boolean))];
    setGenres(uniqueGenres.sort());
  };

  useEffect(() => {
    fetchMovies();
  }, [fetchMovies]);

  useEffect(() => {
    fetchGenres();
    fetchTop10Count();
    fetchSliderCount();
  }, []);

  // Refresh Top 10 and Slider count after bulk actions
  useEffect(() => {
    fetchTop10Count();
    fetchSliderCount();
  }, [movies]);

  // Checkbox handlers
  const toggleSelectMovie = (movieId) => {
    const newSelected = new Set(selectedMovies);
    if (newSelected.has(movieId)) {
      newSelected.delete(movieId);
    } else {
      newSelected.add(movieId);
    }
    setSelectedMovies(newSelected);
  };

  const toggleSelectAll = () => {
    if (selectedMovies.size === movies.length) {
      setSelectedMovies(new Set());
    } else {
      setSelectedMovies(new Set(movies.map(m => m.id)));
    }
  };

  const clearSelection = () => {
    setSelectedMovies(new Set());
  };

  // Bulk actions
  const addToTop10 = async () => {
    if (selectedMovies.size === 0) return;
    if (selectedMovies.size > 10) {
      setBulkActionMessage('❌ Top 10 może mieć maksymalnie 10 filmów!');
      setTimeout(() => setBulkActionMessage(''), 3000);
      return;
    }

    const movieIds = Array.from(selectedMovies);

    // First, remove all current top10 flags
    await supabase
      .from('movies')
      .update({ is_top10: false, top10_order: null })
      .eq('is_top10', true);

    // Then set new top10 movies
    for (let i = 0; i < movieIds.length; i++) {
      await supabase
        .from('movies')
        .update({ is_top10: true, top10_order: i + 1 })
        .eq('id', movieIds[i]);
    }

    setBulkActionMessage(`✅ Ustawiono ${movieIds.length} filmów jako Top 10`);
    setTimeout(() => setBulkActionMessage(''), 3000);
    fetchMovies();
    clearSelection();
  };

  const addSelectedToTop10 = async () => {
    if (selectedMovies.size === 0) return;

    // Get current max top10_order
    const { data: currentTop10 } = await supabase
      .from('movies')
      .select('top10_order')
      .eq('is_top10', true)
      .order('top10_order', { ascending: false })
      .limit(1);

    let nextOrder = (currentTop10?.[0]?.top10_order || 0) + 1;
    const movieIds = Array.from(selectedMovies);

    for (const movieId of movieIds) {
      if (nextOrder <= 10) {
        await supabase
          .from('movies')
          .update({ is_top10: true, top10_order: nextOrder })
          .eq('id', movieId);
        nextOrder++;
      }
    }

    setBulkActionMessage(`✅ Dodano ${Math.min(movieIds.length, 10)} filmów do Top 10`);
    setTimeout(() => setBulkActionMessage(''), 3000);
    fetchMovies();
    clearSelection();
  };

  const removeFromTop10 = async () => {
    if (selectedMovies.size === 0) return;

    const movieIds = Array.from(selectedMovies);

    for (const movieId of movieIds) {
      await supabase
        .from('movies')
        .update({ is_top10: false, top10_order: null })
        .eq('id', movieId);
    }

    setBulkActionMessage(`✅ Usunięto ${movieIds.length} filmów z Top 10`);
    setTimeout(() => setBulkActionMessage(''), 3000);
    fetchMovies();
    clearSelection();
  };

  const toggleActive = async (movie) => {
    const { error } = await supabase
      .from('movies')
      .update({ is_active: !movie.is_active })
      .eq('id', movie.id);

    if (!error) {
      setMovies(
        movies.map((m) =>
          m.id === movie.id ? { ...m, is_active: !m.is_active } : m
        )
      );
    }
  };

  const startEdit = (movie) => {
    setEditingId(movie.id);
    setEditValues({
      title: movie.title,
      genre: movie.genre,
      price: movie.price,
      display_order: movie.display_order,
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditValues({});
  };

  const saveEdit = async (movieId) => {
    const { error } = await supabase
      .from('movies')
      .update(editValues)
      .eq('id', movieId);

    if (!error) {
      setMovies(
        movies.map((m) =>
          m.id === movieId ? { ...m, ...editValues } : m
        )
      );
      setEditingId(null);
      setEditValues({});
    }
  };

  const totalPages = Math.ceil(totalCount / ITEMS_PER_PAGE);

  return (
    <div className="movies-page">
      <h1>Movies Management</h1>

      <div className="filters">
        <input
          type="text"
          placeholder="Search by title..."
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setPage(0);
          }}
          className="search-input"
        />

        <select
          value={genreFilter}
          onChange={(e) => {
            setGenreFilter(e.target.value);
            setPage(0);
          }}
          className="genre-select"
        >
          <option value="">All Genres</option>
          {genres.map((genre) => (
            <option key={genre} value={genre}>
              {genre}
            </option>
          ))}
        </select>

        <button
          onClick={() => {
            setShowTop10Only(!showTop10Only);
            setShowSliderOnly(false);
            setPage(0);
          }}
          className={`top10-filter-btn ${showTop10Only ? 'active' : ''}`}
        >
          ⭐ Top 10 ({top10Count}/10)
        </button>

        <button
          onClick={() => {
            setShowSliderOnly(!showSliderOnly);
            setShowTop10Only(false);
            setPage(0);
          }}
          className={`top10-filter-btn ${showSliderOnly ? 'active' : ''}`}
        >
          📺 Slider ({sliderCount})
        </button>

        <select
          value={sortOrder}
          onChange={(e) => {
            setSortOrder(e.target.value);
            setPage(0);
          }}
          className="sort-select"
        >
          <option value="newest">🕐 Ostatnio dodane</option>
          <option value="oldest">📅 Najstarsze</option>
          <option value="display_order">📋 Display Order</option>
        </select>

        <span className="results-count">
          {totalCount} movies found
        </span>
      </div>

      {/* Bulk Actions Bar */}
      {selectedMovies.size > 0 && (
        <div className="bulk-actions-bar">
          <span className="selected-count">
            ✓ Zaznaczono: {selectedMovies.size} {selectedMovies.size === 1 ? 'film' : 'filmów'}
          </span>

          <div className="bulk-buttons">
            <button onClick={addSelectedToTop10} className="bulk-btn top10">
              ⭐ Dodaj do Top 10
            </button>
            <button onClick={addToTop10} className="bulk-btn top10-replace">
              🔄 Ustaw jako Top 10
            </button>
            <button onClick={removeFromTop10} className="bulk-btn remove-top10">
              ❌ Usuń z Top 10
            </button>
            <button onClick={clearSelection} className="bulk-btn clear">
              ✕ Odznacz wszystko
            </button>
          </div>
        </div>
      )}

      {/* Bulk Action Message */}
      {bulkActionMessage && (
        <div className={`bulk-message ${bulkActionMessage.includes('❌') ? 'error' : 'success'}`}>
          {bulkActionMessage}
        </div>
      )}

      {loading ? (
        <div className="loading">Loading movies...</div>
      ) : (
        <>
          <table className="movies-table">
            <thead>
              <tr>
                <th className="checkbox-col">
                  <input
                    type="checkbox"
                    checked={selectedMovies.size === movies.length && movies.length > 0}
                    onChange={toggleSelectAll}
                    title="Zaznacz wszystkie"
                  />
                </th>
                <th>Poster</th>
                <th>Title</th>
                <th>Genre</th>
                <th>Runtime</th>
                <th>Price</th>
                <th>Order</th>
                <th>Top 10</th>
                <th>Slider</th>
                <th>Active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {movies.map((movie) => (
                <tr
                  key={movie.id}
                  className={`${!movie.is_active ? 'inactive' : ''} ${selectedMovies.has(movie.id) ? 'selected' : ''} ${movie.is_top10 ? 'top10-movie' : ''}`}
                >
                  <td className="checkbox-col">
                    <input
                      type="checkbox"
                      checked={selectedMovies.has(movie.id)}
                      onChange={() => toggleSelectMovie(movie.id)}
                    />
                  </td>
                  <td>
                    <img
                      src={movie.poster_url}
                      alt={movie.title}
                      className="poster-thumb"
                      onError={(e) => {
                        e.target.src = 'https://via.placeholder.com/50x75?text=No+Image';
                      }}
                    />
                  </td>
                  <td>
                    {editingId === movie.id ? (
                      <input
                        type="text"
                        value={editValues.title}
                        onChange={(e) =>
                          setEditValues({ ...editValues, title: e.target.value })
                        }
                        className="edit-input"
                      />
                    ) : (
                      movie.title
                    )}
                  </td>
                  <td>
                    {editingId === movie.id ? (
                      <input
                        type="text"
                        value={editValues.genre}
                        onChange={(e) =>
                          setEditValues({ ...editValues, genre: e.target.value })
                        }
                        className="edit-input small"
                      />
                    ) : (
                      movie.genre
                    )}
                  </td>
                  <td>{movie.runtime}</td>
                  <td>
                    {editingId === movie.id ? (
                      <input
                        type="number"
                        value={editValues.price}
                        onChange={(e) =>
                          setEditValues({
                            ...editValues,
                            price: parseInt(e.target.value) || 0,
                          })
                        }
                        className="edit-input small"
                      />
                    ) : (
                      `${movie.price} zł`
                    )}
                  </td>
                  <td>
                    {editingId === movie.id ? (
                      <input
                        type="number"
                        value={editValues.display_order}
                        onChange={(e) =>
                          setEditValues({
                            ...editValues,
                            display_order: parseInt(e.target.value) || 0,
                          })
                        }
                        className="edit-input small"
                      />
                    ) : (
                      movie.display_order
                    )}
                  </td>
                  <td className="top10-col">
                    {movie.is_top10 && (
                      <span className="top10-badge">#{movie.top10_order}</span>
                    )}
                  </td>
                  <td className="slider-col">
                    {movie.is_slider && (
                      <span className="slider-badge">#{movie.slider_order}</span>
                    )}
                  </td>
                  <td>
                    <button
                      onClick={() => toggleActive(movie)}
                      className={`toggle-btn ${movie.is_active ? 'active' : 'inactive'}`}
                    >
                      {movie.is_active ? 'ON' : 'OFF'}
                    </button>
                  </td>
                  <td>
                    {editingId === movie.id ? (
                      <>
                        <button
                          onClick={() => saveEdit(movie.id)}
                          className="action-btn save"
                        >
                          Save
                        </button>
                        <button
                          onClick={cancelEdit}
                          className="action-btn cancel"
                        >
                          Cancel
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          onClick={() => startEdit(movie)}
                          className="action-btn edit"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => navigate(`/movies/${movie.id}`)}
                          className="action-btn tmdb"
                        >
                          TMDB
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {!showTop10Only && !showSliderOnly && (
            <div className="pagination">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default Movies;
