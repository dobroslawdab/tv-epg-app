import React, { useState, useEffect } from 'react';
import { supabase } from '../supabase';
import ColorThief from 'color-thief-browser';

const TMDB_API_KEY = '716cc02044e4d92d0a012a426902cc2d';
const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

function Slider() {
  const [slots, setSlots] = useState(Array(10).fill(null));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  // Search modal state
  const [searchModal, setSearchModal] = useState({ open: false, slotIndex: null });
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);

  // TMDB modal state
  const [tmdbModal, setTmdbModal] = useState({ open: false, slotIndex: null });
  const [tmdbSearchQuery, setTmdbSearchQuery] = useState('');
  const [tmdbResults, setTmdbResults] = useState([]);
  const [tmdbSearching, setTmdbSearching] = useState(false);

  // Movie preview modal state
  const [previewModal, setPreviewModal] = useState({ open: false, movie: null, slotIndex: null });
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);

  // Wybór assetów + trailer URL przy zatwierdzaniu slotu
  const [selectedPosterUrl, setSelectedPosterUrl] = useState(null);
  const [selectedBackdropUrl, setSelectedBackdropUrl] = useState(null);
  const [selectedLogoUrl, setSelectedLogoUrl] = useState(null);
  const [selectedTrailerUrl, setSelectedTrailerUrl] = useState('');

  // Slider preview state
  const [activeSlide, setActiveSlide] = useState(0);

  // Gradient per slot
  const [slotGradients, setSlotGradients] = useState({});

  // Drag and drop state
  const [draggedSlot, setDraggedSlot] = useState(null);
  const [dragOverSlot, setDragOverSlot] = useState(null);

  // Corner colors for ambient gradient (4 rogi)
  const [cornerColors, setCornerColors] = useState({
    topLeft: '#000000',
    topRight: '#000000',
    bottomLeft: '#000000',
    bottomRight: '#000000'
  });

  // Helpers for current slot gradient
  const currentGradient = slotGradients[activeSlide] || { enabled: false, color: '#000000' };

  const updateCurrentGradient = (updates) => {
    setSlotGradients(prev => ({
      ...prev,
      [activeSlide]: { ...currentGradient, ...updates }
    }));
  };

  // === COLOR EXTRACTION ===
  const colorThief = new ColorThief();

  const rgbToHex = (r, g, b) => '#' + [r, g, b].map(x => x.toString(16).padStart(2, '0')).join('');

  // Extract colors from 4 corners of image
  const extractCornerColors = async (imageUrl) => {
    return new Promise((resolve) => {
      const img = new Image();
      img.crossOrigin = 'Anonymous';
      const proxyUrl = imageUrl.includes('tmdb.org')
        ? `https://images.weserv.nl/?url=${encodeURIComponent(imageUrl)}`
        : imageUrl;

      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          const ctx = canvas.getContext('2d');
          canvas.width = img.width;
          canvas.height = img.height;
          ctx.drawImage(img, 0, 0);

          // Sample size (pixels from corner)
          const sampleSize = 50;

          // Helper to get average color from region
          const getRegionColor = (x, y, w, h) => {
            const data = ctx.getImageData(x, y, w, h).data;
            let r = 0, g = 0, b = 0, count = 0;
            for (let i = 0; i < data.length; i += 4) {
              r += data[i];
              g += data[i + 1];
              b += data[i + 2];
              count++;
            }
            return rgbToHex(
              Math.round(r / count),
              Math.round(g / count),
              Math.round(b / count)
            );
          };

          const corners = {
            topLeft: getRegionColor(0, 0, sampleSize, sampleSize),
            topRight: getRegionColor(img.width - sampleSize, 0, sampleSize, sampleSize),
            bottomLeft: getRegionColor(0, img.height - sampleSize, sampleSize, sampleSize),
            bottomRight: getRegionColor(img.width - sampleSize, img.height - sampleSize, sampleSize, sampleSize)
          };

          resolve(corners);
        } catch (err) {
          console.error('Corner color extraction failed:', err);
          resolve(null);
        }
      };
      img.onerror = () => resolve(null);
      img.src = proxyUrl;
    });
  };

  const extractDominantColor = async (imageUrl) => {
    return new Promise((resolve) => {
      const img = new Image();
      img.crossOrigin = 'Anonymous';
      // Use CORS proxy for TMDB images
      const proxyUrl = imageUrl.includes('tmdb.org')
        ? `https://images.weserv.nl/?url=${encodeURIComponent(imageUrl)}`
        : imageUrl;
      img.onload = () => {
        try {
          const color = colorThief.getColor(img);
          resolve(rgbToHex(color[0], color[1], color[2]));
        } catch (err) {
          console.error('Color extraction failed:', err);
          resolve(null);
        }
      };
      img.onerror = () => {
        console.error('Image load failed for color extraction');
        resolve(null);
      };
      img.src = proxyUrl;
    });
  };

  const saveGlowColorToDatabase = async (movieId, hexColor) => {
    if (!movieId) return;
    const { error } = await supabase
      .from('movies')
      .update({ slider_glow_color: hexColor })
      .eq('id', String(movieId));
    if (error) {
      console.error('Error saving glow color:', error);
      setMessage('Błąd zapisu koloru: ' + error.message);
    }
  };

  const autoExtractAndSaveColor = async (slotIndex) => {
    const slot = slots[slotIndex];
    if (!slot?.backdrop_url) {
      setMessage('Brak obrazu backdrop do ekstrakcji koloru');
      return;
    }
    setMessage('🎨 Ekstrakcja koloru...');
    const extractedColor = await extractDominantColor(slot.backdrop_url);
    if (extractedColor) {
      setSlotGradients(prev => ({
        ...prev,
        [slotIndex]: { enabled: true, color: extractedColor }
      }));
      await saveGlowColorToDatabase(slot.id, extractedColor);
      // Update local slot state with the color
      const newSlots = [...slots];
      newSlots[slotIndex] = { ...slot, slider_glow_color: extractedColor };
      setSlots(newSlots);
      setMessage(`✅ Kolor ${extractedColor} wyekstrahowany i zapisany!`);
    } else {
      setMessage('❌ Nie udało się wyekstrahować koloru');
    }
    setTimeout(() => setMessage(''), 3000);
  };

  const handleColorChange = async (newColor) => {
    updateCurrentGradient({ color: newColor });
    const currentSlot = slots[activeSlide];
    if (currentSlot?.id) {
      await saveGlowColorToDatabase(currentSlot.id, newColor);
      // Update local slot state
      const newSlots = [...slots];
      newSlots[activeSlide] = { ...currentSlot, slider_glow_color: newColor };
      setSlots(newSlots);
    }
  };
  // === END COLOR EXTRACTION ===

  useEffect(() => {
    fetchSlots();
  }, []);

  // Auto-extract corner colors when slide changes
  useEffect(() => {
    const currentSlot = slots[activeSlide];
    if (currentSlot?.backdrop_url) {
      extractCornerColors(currentSlot.backdrop_url).then(colors => {
        if (colors) {
          setCornerColors(colors);
        }
      });
    }
  }, [activeSlide, slots]);


  const fetchSlots = async () => {
    setLoading(true);
    // Single Source of Truth: pobierz filmy z tabeli movies gdzie is_slider=true
    const { data, error } = await supabase
      .from('movies')
      .select('*')
      .eq('is_slider', true)
      .order('slider_order');

    console.log('=== FETCH SLOTS DEBUG ===');
    console.log('Raw data from Supabase:', data);

    if (error) {
      console.error('Error fetching slots:', error);
      setMessage('Błąd pobierania slotów: ' + error.message);
    } else {
      // Map data to slots array (index 0-9 for positions 1-10)
      const slotsArray = Array(10).fill(null);
      const loadedGradients = {};
      data?.forEach(movie => {
        console.log(`Movie: "${movie.title}" | ID: ${movie.id} | TMDB: ${movie.tmdb_id} | Order: ${movie.slider_order}`);

        if (movie.slider_order >= 1 && movie.slider_order <= 10) {
          const index = movie.slider_order - 1;
          // Mapuj pola z movies na format slotu
          slotsArray[index] = {
            ...movie,
            slot_position: movie.slider_order, // kompatybilność wsteczna
            movie_id: movie.id, // movie_id = id z tabeli movies
          };
          // Load saved glow color into gradients state
          if (movie.slider_glow_color) {
            loadedGradients[index] = {
              enabled: true,
              color: movie.slider_glow_color
            };
          }
        }
      });
      console.log('Mapped slots:', slotsArray.map(s => s ? `${s.title} (ID: ${s.id})` : 'empty'));
      setSlots(slotsArray);
      setSlotGradients(prev => ({ ...prev, ...loadedGradients }));
    }
    setLoading(false);
  };

  // Search movies in Supabase
  const searchMovies = async () => {
    if (!searchQuery.trim()) return;
    setSearching(true);

    const { data, error } = await supabase
      .from('movies')
      .select('*')
      .ilike('title', `%${searchQuery}%`)
      .limit(20);

    if (!error) {
      setSearchResults(data || []);
    }
    setSearching(false);
  };

  // Open movie preview with full TMDB data
  const openMoviePreview = async (movie, slotIndex) => {
    setPreviewModal({ open: true, movie, slotIndex });
    setPreviewLoading(true);
    setPreviewData(null);

    // PRIMARY SOURCE: dane już zsynchronizowane w bazie (movies.tmdb_*).
    // Fallback do TMDB API tylko gdy w bazie pusto a movie ma tmdb_id.
    const hasDbAssets = (movie.tmdb_posters?.length || 0) > 0
        || (movie.tmdb_backdrops?.length || 0) > 0
        || (movie.tmdb_logos?.length || 0) > 0;

    if (hasDbAssets) {
      const directors = (movie.tmdb_directors || []).map(d => d.name || d);
      const cast = (movie.tmdb_cast || []).slice(0, 10).map(a => ({
        name: a.name, character: a.character, profile_path: a.profile_path
      }));
      const videos = (movie.tmdb_videos || []).filter(v => v.site === 'YouTube').slice(0, 5);
      const backdrops = (movie.tmdb_backdrops || []).slice(0, 6);
      const logos = (movie.tmdb_logos || []).slice(0, 3);
      const posters = (movie.tmdb_posters || []).slice(0, 12);

      const defaultBackdrop = movie.backdrop_url
        || (movie.tmdb_backdrops?.[0]?.file_path ? `${TMDB_IMAGE_BASE}/original${movie.tmdb_backdrops[0].file_path}` : null);
      const defaultPoster = movie.poster_url
        || movie.tmdb_poster_url
        || (movie.tmdb_posters?.[0]?.file_path ? `${TMDB_IMAGE_BASE}/original${movie.tmdb_posters[0].file_path}` : null);
      const defaultLogo = movie.selected_logo_url
        || movie.tmdb_logo_url
        || (movie.tmdb_logos?.[0]?.file_path ? `${TMDB_IMAGE_BASE}/original${movie.tmdb_logos[0].file_path}` : null);

      setPreviewData({
        ...movie,
        tmdb_details: {
          release_date: movie.tmdb_release_date,
          runtime: movie.tmdb_runtime,
          vote_average: movie.tmdb_vote_average,
          genres: movie.tmdb_genres || [],
          overview: movie.tmdb_overview
        },
        tmdb_directors: directors,
        tmdb_cast: cast,
        tmdb_videos: videos,
        tmdb_backdrops: backdrops,
        tmdb_logos: logos,
        tmdb_posters: posters,
        backdrop_url: defaultBackdrop,
        poster_url: defaultPoster,
        logo_url: defaultLogo,
      });
      setSelectedBackdropUrl(defaultBackdrop);
      setSelectedPosterUrl(defaultPoster);
      setSelectedLogoUrl(defaultLogo);
      setSelectedTrailerUrl(movie.youtube_url || '');
      setPreviewLoading(false);
      return;
    }

    // Fallback: brak danych w bazie — fetch z TMDB
    if (movie.tmdb_id) {
      try {
        const [detailsRes, videosRes, imagesRes] = await Promise.all([
          fetch(`https://api.themoviedb.org/3/movie/${movie.tmdb_id}?api_key=${TMDB_API_KEY}&language=pl-PL&append_to_response=credits`),
          fetch(`https://api.themoviedb.org/3/movie/${movie.tmdb_id}/videos?api_key=${TMDB_API_KEY}`),
          fetch(`https://api.themoviedb.org/3/movie/${movie.tmdb_id}/images?api_key=${TMDB_API_KEY}`)
        ]);

        const details = await detailsRes.json();
        const videosData = await videosRes.json();
        const imagesData = await imagesRes.json();

        const directors = details.credits?.crew?.filter(p => p.job === 'Director').map(p => p.name) || [];
        const cast = details.credits?.cast?.slice(0, 10).map(a => ({ name: a.name, character: a.character, profile_path: a.profile_path })) || [];
        const videos = videosData.results?.filter(v => v.site === 'YouTube').slice(0, 5) || [];
        const backdrops = imagesData.backdrops?.slice(0, 6) || [];
        const logos = imagesData.logos?.slice(0, 3) || [];
        const posters = imagesData.posters?.slice(0, 12) || [];

        const defaultBackdrop = details.backdrop_path ? `${TMDB_IMAGE_BASE}/original${details.backdrop_path}` : movie.backdrop_url;
        const defaultPoster = details.poster_path ? `${TMDB_IMAGE_BASE}/original${details.poster_path}` : movie.poster_url;
        const defaultLogo = imagesData.logos?.[0]?.file_path
          ? `${TMDB_IMAGE_BASE}/original${imagesData.logos[0].file_path}`
          : movie.selected_logo_url || movie.tmdb_logo_url;

        setPreviewData({
          ...movie,
          tmdb_details: details,
          tmdb_directors: directors,
          tmdb_cast: cast,
          tmdb_videos: videos,
          tmdb_backdrops: backdrops,
          tmdb_logos: logos,
          tmdb_posters: posters,
          backdrop_url: defaultBackdrop,
          poster_url: defaultPoster,
          logo_url: defaultLogo,
        });
        // Pre-fill wybory zatwierdzenia
        setSelectedBackdropUrl(defaultBackdrop);
        setSelectedPosterUrl(defaultPoster);
        setSelectedLogoUrl(defaultLogo);
        setSelectedTrailerUrl(movie.youtube_url || '');
      } catch (err) {
        console.error('TMDB fetch error:', err);
        setPreviewData(movie); // fallback to local data
        setSelectedBackdropUrl(movie.backdrop_url);
        setSelectedPosterUrl(movie.poster_url);
        setSelectedLogoUrl(movie.selected_logo_url || movie.tmdb_logo_url);
        setSelectedTrailerUrl(movie.youtube_url || '');
      }
    } else {
      setPreviewData(movie); // No TMDB ID, use local data
      setSelectedBackdropUrl(movie.backdrop_url);
      setSelectedPosterUrl(movie.poster_url);
      setSelectedLogoUrl(movie.selected_logo_url || movie.tmdb_logo_url);
      setSelectedTrailerUrl(movie.youtube_url || '');
    }
    setPreviewLoading(false);
  };

  // Confirm movie selection from preview
  // Single Source of Truth: oznacz film jako slider w tabeli movies
  const confirmMovieSelection = async () => {
    const { movie, slotIndex } = previewModal;
    const slotPosition = slotIndex + 1;

    try {
      // 1. Usuń poprzedni film z tego slotu (jeśli istnieje)
      const currentSlot = slots[slotIndex];
      if (currentSlot?.id) {
        await supabase
          .from('movies')
          .update({ is_slider: false, slider_order: null })
          .eq('id', String(currentSlot.id));
      }

      // 2. Oznacz wybrany film jako slider — z wybranymi assetami i trailerem
      const updateData = {
        is_slider: true,
        slider_order: slotPosition,
        backdrop_url: selectedBackdropUrl || previewData?.backdrop_url || movie.backdrop_url,
        poster_url: selectedPosterUrl || previewData?.poster_url || movie.poster_url,
        selected_logo_url: selectedLogoUrl || previewData?.logo_url || movie.selected_logo_url || movie.tmdb_logo_url,
        youtube_url: selectedTrailerUrl.trim() || movie.youtube_url || null,
      };

      const { error } = await supabase
        .from('movies')
        .update(updateData)
        .eq('id', String(movie.id));

      if (error) {
        setMessage('Błąd zapisywania: ' + error.message);
      } else {
        const newSlots = [...slots];
        newSlots[slotIndex] = {
          ...movie,
          ...updateData,
          slot_position: slotPosition,
          movie_id: movie.id
        };
        setSlots(newSlots);
        setMessage(`✅ Film "${movie.title}" dodany do slotu ${slotPosition}`);
      }
    } catch (err) {
      setMessage('Błąd: ' + err.message);
    }

    setPreviewModal({ open: false, movie: null, slotIndex: null });
    setSearchModal({ open: false, slotIndex: null });
    setSearchQuery('');
    setSearchResults([]);
    setPreviewData(null);
    setTimeout(() => setMessage(''), 3000);
  };

  // Search TMDB
  const searchTMDB = async () => {
    if (!tmdbSearchQuery.trim()) return;
    setTmdbSearching(true);

    try {
      const url = `https://api.themoviedb.org/3/search/movie?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(tmdbSearchQuery)}&language=pl-PL`;
      const res = await fetch(url);
      const data = await res.json();
      setTmdbResults(data.results || []);
    } catch (err) {
      setMessage('Błąd TMDB: ' + err.message);
    }
    setTmdbSearching(false);
  };

  // Fetch full TMDB data and update movie in movies table
  // Single Source of Truth: aktualizuj dane TMDB bezpośrednio w tabeli movies
  const selectTmdbMovie = async (tmdbMovie) => {
    const slotIndex = tmdbModal.slotIndex;
    const currentSlot = slots[slotIndex];

    // DEBUG: Log IDs to find the issue
    console.log('=== TMDB UPDATE DEBUG ===');
    console.log('currentSlot:', currentSlot);
    console.log('currentSlot.id:', currentSlot?.id);
    console.log('currentSlot.movie_id:', currentSlot?.movie_id);
    console.log('tmdbMovie.id (TMDB ID):', tmdbMovie.id);

    // Użyj prawidłowego ID z Supabase (nie TMDB ID!)
    const supabaseId = currentSlot?.id;

    if (!supabaseId) {
      setMessage('Błąd: Brak ID filmu w slocie. Sprawdź czy film został poprawnie załadowany z bazy.');
      console.error('Missing Supabase ID. currentSlot:', currentSlot);
      return;
    }

    // Sprawdź czy ID wygląda jak UUID (nie jak TMDB ID)
    const isUUID = supabaseId.includes('-') || supabaseId.length > 20;
    if (!isUUID) {
      console.warn('WARNING: ID nie wygląda jak UUID:', supabaseId);
    }

    setTmdbSearching(true);
    setMessage('🎬 Pobieranie danych z TMDB...');

    try {
      // Get full details
      const detailsUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}?api_key=${TMDB_API_KEY}&language=pl-PL&append_to_response=credits`;
      const detailsRes = await fetch(detailsUrl);
      const details = await detailsRes.json();

      // Get videos
      const videosUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/videos?api_key=${TMDB_API_KEY}`;
      const videosRes = await fetch(videosUrl);
      const videosData = await videosRes.json();

      // Get images
      const imagesUrl = `https://api.themoviedb.org/3/movie/${tmdbMovie.id}/images?api_key=${TMDB_API_KEY}`;
      const imagesRes = await fetch(imagesUrl);
      const imagesData = await imagesRes.json();

      // Extract data
      const logo = imagesData.logos?.[0]?.file_path;
      const youtubeTrailer = videosData.results?.find(v => v.site === 'YouTube' && v.type === 'Trailer');

      console.log('Found trailer:', youtubeTrailer);
      console.log('YouTube URL will be:', youtubeTrailer ? `https://www.youtube.com/watch?v=${youtubeTrailer.key}` : 'none');

      // Update movies table (Single Source of Truth)
      const updateData = {
        tmdb_id: details.id,
        backdrop_url: details.backdrop_path ? `${TMDB_IMAGE_BASE}/original${details.backdrop_path}` : currentSlot?.backdrop_url,
        poster_url: details.poster_path ? `${TMDB_IMAGE_BASE}/original${details.poster_path}` : currentSlot?.poster_url,
        logo_url: logo ? `${TMDB_IMAGE_BASE}/original${logo}` : currentSlot?.logo_url,
        description: details.overview || currentSlot?.description,
        release_year: details.release_date?.substring(0, 4),
        runtime: details.runtime ? `${details.runtime} min` : currentSlot?.runtime,
        vote_average: details.vote_average,
        genre: details.genres?.map(g => g.name).join(', ') || currentSlot?.genre,
        youtube_url: youtubeTrailer ? `https://www.youtube.com/watch?v=${youtubeTrailer.key}` : currentSlot?.youtube_url,
      };

      console.log('Updating Supabase with ID:', supabaseId);
      console.log('Update data:', updateData);

      const { data, error } = await supabase
        .from('movies')
        .update(updateData)
        .eq('id', String(supabaseId))
        .select();

      console.log('Supabase response:', { data, error });

      if (error) {
        console.error('Supabase error:', error);
        setMessage('Błąd zapisywania TMDB: ' + error.message + ' (ID: ' + supabaseId + ')');
      } else {
        const newSlots = [...slots];
        newSlots[slotIndex] = { ...currentSlot, ...updateData };
        setSlots(newSlots);
        const trailerInfo = youtubeTrailer ? ` 🎥 Trailer: ${youtubeTrailer.key}` : ' (brak trailera)';
        setMessage(`✅ Dane TMDB dla "${details.title}" zaktualizowane!${trailerInfo}`);
      }
    } catch (err) {
      console.error('TMDB fetch error:', err);
      setMessage('Błąd: ' + err.message);
    }

    setTmdbModal({ open: false, slotIndex: null });
    setTmdbSearchQuery('');
    setTmdbResults([]);
    setTmdbSearching(false);
    setTimeout(() => setMessage(''), 5000);
  };

  // Remove movie from slider (not from database!)
  // Single Source of Truth: tylko usuń flagę is_slider i slider_order
  const removeFromSlot = async (slotIndex) => {
    const slotPosition = slotIndex + 1;
    const currentSlot = slots[slotIndex];

    if (!currentSlot?.id) {
      setMessage('Slot jest już pusty');
      return;
    }

    // Tylko usuń z slidera - film pozostaje w bazie
    const { error } = await supabase
      .from('movies')
      .update({ is_slider: false, slider_order: null })
      .eq('id', String(currentSlot.id));

    if (!error) {
      const newSlots = [...slots];
      newSlots[slotIndex] = null; // Slot pusty
      setSlots(newSlots);
      setMessage(`🗑️ Film "${currentSlot.title}" usunięty ze slotu ${slotPosition}`);
      setTimeout(() => setMessage(''), 3000);
    } else {
      setMessage('Błąd: ' + error.message);
    }
  };

  // Drag and drop handlers
  const handleDragStart = (e, slotIndex) => {
    setDraggedSlot(slotIndex);
    e.dataTransfer.effectAllowed = 'move';
    // Add visual feedback
    setTimeout(() => {
      e.target.classList.add('dragging');
    }, 0);
  };

  const handleDragEnd = (e) => {
    e.target.classList.remove('dragging');
    setDraggedSlot(null);
    setDragOverSlot(null);
  };

  const handleDragOver = (e, slotIndex) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (slotIndex !== draggedSlot) {
      setDragOverSlot(slotIndex);
    }
  };

  const handleDragLeave = () => {
    setDragOverSlot(null);
  };

  const handleDrop = async (e, targetIndex) => {
    e.preventDefault();
    setDragOverSlot(null);

    if (draggedSlot === null || draggedSlot === targetIndex) {
      return;
    }

    // Swap slots in database and UI
    await swapSlots(draggedSlot, targetIndex);
    setDraggedSlot(null);
  };

  // Swap two slots - just swap slider_order values
  // Single Source of Truth: tylko zamień slider_order między dwoma filmami
  const swapSlots = async (fromIndex, toIndex) => {
    setSaving(true);
    const fromPosition = fromIndex + 1;
    const toPosition = toIndex + 1;

    const fromSlot = slots[fromIndex];
    const toSlot = slots[toIndex];

    try {
      // Zamień slider_order między dwoma filmami
      if (fromSlot?.id && toSlot?.id) {
        // Oba sloty mają filmy - zamień pozycje
        const { error: error1 } = await supabase
          .from('movies')
          .update({ slider_order: toPosition })
          .eq('id', String(fromSlot.id));

        const { error: error2 } = await supabase
          .from('movies')
          .update({ slider_order: fromPosition })
          .eq('id', String(toSlot.id));

        if (error1 || error2) {
          setMessage('❌ Błąd zamiany slotów: ' + (error1?.message || error2?.message));
        } else {
          const newSlots = [...slots];
          newSlots[fromIndex] = { ...toSlot, slider_order: fromPosition, slot_position: fromPosition };
          newSlots[toIndex] = { ...fromSlot, slider_order: toPosition, slot_position: toPosition };
          setSlots(newSlots);
          setMessage(`✅ Zamieniono slot ${fromPosition} ↔ ${toPosition}`);
        }
      } else if (fromSlot?.id && !toSlot?.id) {
        // Przenieś film z fromSlot do pustego toSlot
        const { error } = await supabase
          .from('movies')
          .update({ slider_order: toPosition })
          .eq('id', String(fromSlot.id));

        if (!error) {
          const newSlots = [...slots];
          newSlots[toIndex] = { ...fromSlot, slider_order: toPosition, slot_position: toPosition };
          newSlots[fromIndex] = null;
          setSlots(newSlots);
          setMessage(`✅ Przeniesiono do slotu ${toPosition}`);
        } else {
          setMessage('❌ Błąd: ' + error.message);
        }
      }
    } catch (err) {
      setMessage('❌ Błąd: ' + err.message);
    }

    setSaving(false);
    setTimeout(() => setMessage(''), 3000);
  };

  if (loading) {
    return <div className="loading">Ładowanie slotów...</div>;
  }

  // Get current active slide data
  const currentSlide = slots[activeSlide];
  const filledSlotsCount = slots.filter(s => s?.title).length;

  return (
    <div className="slider-page">
      <h1>🖼️ Slider - Wybierz 10 filmów</h1>

      {message && (
        <div className={`message ${message.includes('Błąd') ? 'error' : 'success'}`}>
          {message}
        </div>
      )}

      {/* SLIDER PREVIEW - jak na TV */}
      <div className="slider-preview-section">
        <h2>📺 Podgląd Slidera ({filledSlotsCount}/10 filmów)</h2>

        <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-start' }}>
          {/* Ambient Light Container - 4 kolory z rogów obrazka */}
          <div
            className="ambient-light-container"
            style={{
              flex: 1,
              padding: '40px',
              background: `conic-gradient(
                from 225deg at 50% 50%,
                ${cornerColors.bottomLeft} 0deg,
                ${cornerColors.topLeft} 90deg,
                ${cornerColors.topRight} 180deg,
                ${cornerColors.bottomRight} 270deg,
                ${cornerColors.bottomLeft} 360deg
              )`,
              borderRadius: '16px',
              transition: 'all 0.5s ease'
            }}
          >
          <div className="tv-slider-preview" style={{ flex: 1 }}>
          {/* Main slide */}
          <div className="tv-slide">
            {currentSlide?.backdrop_url ? (
              <img
                src={currentSlide.backdrop_url}
                alt={currentSlide.title}
                className="tv-backdrop"
              />
            ) : (
              <div className="tv-backdrop-empty">
                <span>Brak filmu w slocie {activeSlide + 1}</span>
              </div>
            )}

            {/* Przyciemnienie całej grafiki - 20% */}
            <div
              className="tv-darken-overlay"
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                background: 'rgba(0, 0, 0, 0.20)',
                pointerEvents: 'none'
              }}
            />


            {/* Content - tytuł i metadane na dole */}
            <div
              className="tv-content"
              style={{
                position: 'absolute',
                bottom: '30px',
                left: '40px',
                right: '40px'
              }}
            >
              {currentSlide?.title && (
                <>
                  <h2
                    className="tv-title-text"
                    style={{
                      color: '#ffffff',
                      margin: '0 0 12px 0',
                      fontSize: '56px',
                      fontWeight: '600',
                      textShadow: currentGradient.enabled
                        ? `0 0 60px ${currentGradient.color}, 0 0 120px ${currentGradient.color}, 0 0 180px ${currentGradient.color}, 0 0 250px ${currentGradient.color}, 0 4px 30px rgba(0,0,0,0.9)`
                        : 'none'
                    }}
                  >
                    {currentSlide.title}
                  </h2>

                  <div
                    className="tv-meta"
                    style={{
                      color: 'rgba(255,255,255,0.9)',
                      fontSize: '14px',
                      display: 'flex',
                      gap: '15px',
                      textShadow: currentGradient.enabled
                        ? `0 0 40px ${currentGradient.color}, 0 0 80px ${currentGradient.color}, 0 0 120px ${currentGradient.color}, 0 2px 15px rgba(0,0,0,0.9)`
                        : 'none'
                    }}
                  >
                    {currentSlide.own_runtime && <span>{currentSlide.own_runtime}</span>}
                    {!currentSlide.own_runtime && currentSlide.runtime && <span>{currentSlide.runtime} min</span>}
                    {currentSlide.own_genre && <span>{currentSlide.own_genre}</span>}
                    {!currentSlide.own_genre && currentSlide.genres?.length > 0 && (
                      <span>{currentSlide.genres.map(g => g.name || g).join(' • ')}</span>
                    )}
                  </div>

                  {(currentSlide.short_description || currentSlide.tmdb_overview) && (
                    <p
                      className="tv-description"
                      style={{
                        color: 'rgba(255,255,255,0.9)',
                        fontSize: '14px',
                        lineHeight: '1.5',
                        margin: '12px 0 0 0',
                        maxWidth: '500px',
                        textShadow: currentGradient.enabled
                          ? `0 0 40px ${currentGradient.color}, 0 0 80px ${currentGradient.color}, 0 0 120px ${currentGradient.color}, 0 2px 15px rgba(0,0,0,0.9)`
                          : 'none'
                      }}
                    >
                      {currentSlide.short_description || currentSlide.tmdb_overview}
                    </p>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
          </div>
          {/* Koniec Ambient Light Container */}

          {/* Panel kontrolek gradientu - per slot */}
          <div
            className="gradient-controls"
            style={{
              background: '#2a2a3e',
              borderRadius: '12px',
              padding: '20px',
              minWidth: '220px',
              display: 'flex',
              flexDirection: 'column',
              gap: '15px'
            }}
          >
            <h3 style={{ margin: 0, fontSize: '16px', color: '#fff' }}>
              🎨 Cień tekstu (Slot {activeSlide + 1})
            </h3>

            <label style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', color: '#ccc' }}>
              <input
                type="checkbox"
                checked={currentGradient.enabled}
                onChange={(e) => updateCurrentGradient({ enabled: e.target.checked })}
                style={{ width: '18px', height: '18px', cursor: 'pointer' }}
              />
              Pokaż cień tekstu
            </label>

            {currentGradient.enabled && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <label style={{ color: '#ccc', fontSize: '14px' }}>Kolor cienia:</label>
                {/* Podgląd koloru jako tło slidera */}
                <div
                  style={{
                    width: '100%',
                    height: '60px',
                    borderRadius: '8px',
                    background: currentGradient.color,
                    marginBottom: '10px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    border: '1px solid #444'
                  }}
                >
                  <span style={{
                    color: '#fff',
                    textShadow: `0 0 20px ${currentGradient.color}, 0 0 40px ${currentGradient.color}`,
                    fontSize: '14px',
                    fontWeight: '600'
                  }}>
                    Podgląd cienia
                  </span>
                </div>
                <input
                  type="color"
                  value={currentGradient.color}
                  onChange={(e) => handleColorChange(e.target.value)}
                  style={{
                    width: '100%',
                    height: '40px',
                    border: 'none',
                    borderRadius: '8px',
                    cursor: 'pointer'
                  }}
                />
                <div style={{ fontSize: '12px', color: '#888' }}>{currentGradient.color}</div>

                {/* Auto-ekstrakcja koloru z backdrop */}
                <button
                  onClick={() => autoExtractAndSaveColor(activeSlide)}
                  disabled={!currentSlide?.backdrop_url}
                  style={{
                    background: currentSlide?.backdrop_url ? 'linear-gradient(135deg, #5AECD3, #4fd1c5)' : '#555',
                    color: currentSlide?.backdrop_url ? '#1a1a2e' : '#888',
                    border: 'none',
                    borderRadius: '8px',
                    padding: '10px 15px',
                    fontSize: '14px',
                    fontWeight: '600',
                    cursor: currentSlide?.backdrop_url ? 'pointer' : 'not-allowed',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '8px',
                    transition: 'all 0.2s ease'
                  }}
                >
                  🎨 Auto-ekstrakcja
                </button>
              </div>
            )}

            {/* Ambient Light - kolory z 4 rogów */}
            <div style={{ borderTop: '1px solid #444', paddingTop: '10px', marginTop: '5px' }}>
              <div style={{ fontSize: '12px', color: '#888', marginBottom: '8px' }}>🌈 Ambient (4 rogi):</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px', marginBottom: '10px' }}>
                <div style={{ background: cornerColors.topLeft, height: '30px', borderRadius: '4px 0 0 0', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '8px', color: '#fff', textShadow: '0 0 3px #000' }}>TL</div>
                <div style={{ background: cornerColors.topRight, height: '30px', borderRadius: '0 4px 0 0', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '8px', color: '#fff', textShadow: '0 0 3px #000' }}>TR</div>
                <div style={{ background: cornerColors.bottomLeft, height: '30px', borderRadius: '0 0 0 4px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '8px', color: '#fff', textShadow: '0 0 3px #000' }}>BL</div>
                <div style={{ background: cornerColors.bottomRight, height: '30px', borderRadius: '0 0 4px 0', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '8px', color: '#fff', textShadow: '0 0 3px #000' }}>BR</div>
              </div>
            </div>

            {/* Podgląd ustawień wszystkich slotów */}
            <div style={{ borderTop: '1px solid #444', paddingTop: '10px', marginTop: '5px' }}>
              <div style={{ fontSize: '12px', color: '#888', marginBottom: '8px' }}>Cienie slotów:</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '5px' }}>
                {[0,1,2,3,4,5,6,7,8,9].map(i => {
                  const g = slotGradients[i];
                  return (
                    <div
                      key={i}
                      onClick={() => setActiveSlide(i)}
                      style={{
                        width: '24px',
                        height: '24px',
                        borderRadius: '4px',
                        background: g?.enabled ? g.color : '#444',
                        border: i === activeSlide ? '2px solid #5AECD3' : '1px solid #555',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '10px',
                        color: '#fff'
                      }}
                    >
                      {i + 1}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>

        {/* Slot indicators - PONIŻEJ podglądu */}
        <div className="tv-slot-indicators">
          {slots.map((slot, index) => (
            <button
              key={index}
              className={`tv-slot-btn ${index === activeSlide ? 'active' : ''} ${slot?.title ? 'filled' : 'empty'}`}
              onClick={() => setActiveSlide(index)}
            >
              <span className="slot-num">{index + 1}</span>
              <span className="slot-label">{slot?.title ? slot.title.substring(0, 15) + (slot.title.length > 15 ? '...' : '') : 'Pusty'}</span>
            </button>
          ))}
        </div>
      </div>

      {/* SLOTS EDITOR */}
      <div className="slots-editor-section">
        <h2>✏️ Edycja slotów</h2>
        <p className="slider-hint">🔄 Przeciągnij slot aby zmienić kolejność. Kliknij "Wybierz film" aby dodać.</p>
      </div>

      <div className="slots-container">
        {slots.map((slot, index) => (
          <div
            key={index}
            className={`slot-card ${slot?.title ? 'filled' : 'empty'} ${draggedSlot === index ? 'dragging' : ''} ${dragOverSlot === index ? 'drag-over' : ''}`}
            draggable={!!slot?.title}
            onDragStart={(e) => handleDragStart(e, index)}
            onDragEnd={handleDragEnd}
            onDragOver={(e) => handleDragOver(e, index)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, index)}>
            <div className="slot-header">
              <span className="slot-number">SLOT {index + 1}</span>
              {slot?.vote_average && (
                <span className="slot-rating">⭐ {slot.vote_average.toFixed(1)}</span>
              )}
            </div>

            {slot?.backdrop_url ? (
              <div className="slot-backdrop">
                <img src={slot.backdrop_url} alt={slot.title} />
                {slot.logo_url && (
                  <img src={slot.logo_url} alt="Logo" className="slot-logo" />
                )}
              </div>
            ) : (
              <div className="slot-backdrop empty-backdrop">
                <span>Brak filmu</span>
              </div>
            )}

            <div className="slot-content">
              {slot?.title ? (
                <>
                  <h3 className="slot-title">{slot.title}</h3>
                  <div className="slot-meta">
                    {slot.release_year && <span>{slot.release_year}</span>}
                    {slot.runtime && <span>{slot.runtime}</span>}
                    {slot.tmdb_id && <span style={{ color: '#5AECD3' }}>TMDB: {slot.tmdb_id}</span>}
                  </div>
                  {slot.short_description && (
                    <p className="slot-description">{slot.short_description.substring(0, 150)}...</p>
                  )}
                  {!slot.short_description && slot.description && (
                    <p className="slot-description">{slot.description.substring(0, 150)}...</p>
                  )}
                  {/* YouTube URL indicator */}
                  {slot.youtube_url && (
                    <div style={{ marginTop: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span style={{ color: '#ff0000', fontSize: '14px' }}>▶</span>
                      <a
                        href={slot.youtube_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{ color: '#5AECD3', fontSize: '12px', textDecoration: 'none' }}
                        onClick={e => e.stopPropagation()}
                      >
                        🎬 Trailer
                      </a>
                    </div>
                  )}
                  {/* Glow color indicator */}
                  {slot.slider_glow_color && (
                    <div style={{ marginTop: '4px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <div style={{
                        width: '14px',
                        height: '14px',
                        borderRadius: '3px',
                        background: slot.slider_glow_color,
                        border: '1px solid #fff'
                      }} />
                      <span style={{ color: '#888', fontSize: '11px' }}>{slot.slider_glow_color}</span>
                    </div>
                  )}
                  {/* Debug: Show Supabase ID */}
                  <div style={{ marginTop: '4px', fontSize: '10px', color: '#666', fontFamily: 'monospace' }}>
                    ID: {slot.id?.substring(0, 8)}...
                  </div>
                </>
              ) : (
                <p className="empty-text">Kliknij "Wybierz film" aby dodać</p>
              )}
            </div>

            <div className="slot-actions">
              <button
                onClick={() => setSearchModal({ open: true, slotIndex: index })}
                className="slot-btn search"
              >
                🔍 Wybierz film
              </button>
              {slot?.title && (
                <>
                  <button
                    onClick={() => {
                      setTmdbModal({ open: true, slotIndex: index });
                      setTmdbSearchQuery(slot.title);
                    }}
                    className="slot-btn tmdb"
                  >
                    🎬 TMDB
                  </button>
                  <button
                    onClick={() => removeFromSlot(index)}
                    className="slot-btn remove"
                  >
                    🗑️
                  </button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Search Modal - Movies from Supabase */}
      {searchModal.open && (
        <div className="modal-overlay" onClick={() => setSearchModal({ open: false, slotIndex: null })}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2>🔍 Wybierz film dla Slotu {searchModal.slotIndex + 1}</h2>

            <div className="modal-search">
              <input
                type="text"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                onKeyPress={e => e.key === 'Enter' && searchMovies()}
                placeholder="Wyszukaj po tytule..."
                autoFocus
              />
              <button onClick={searchMovies} disabled={searching}>
                {searching ? '...' : '🔍'}
              </button>
            </div>

            <div className="modal-results">
              {searchResults.map(movie => (
                <div
                  key={movie.id}
                  className="result-item"
                  onClick={() => openMoviePreview(movie, searchModal.slotIndex)}
                >
                  <img
                    src={movie.poster_url || 'https://via.placeholder.com/50x75?text=No'}
                    alt={movie.title}
                  />
                  <div className="result-info">
                    <span className="result-title">{movie.title}</span>
                    <span className="result-genre">{movie.genre}</span>
                    {movie.short_description && (
                      <span className="result-desc">{movie.short_description.substring(0, 80)}...</span>
                    )}
                  </div>
                  <span className="result-arrow">→</span>
                </div>
              ))}
              {searchResults.length === 0 && searchQuery && !searching && (
                <p className="no-results">Brak wyników dla "{searchQuery}"</p>
              )}
            </div>

            <button className="modal-close" onClick={() => setSearchModal({ open: false, slotIndex: null })}>
              ✕ Zamknij
            </button>
          </div>
        </div>
      )}

      {/* TMDB Modal */}
      {tmdbModal.open && (
        <div className="modal-overlay" onClick={() => setTmdbModal({ open: false, slotIndex: null })}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2>🎬 TMDB - Slot {tmdbModal.slotIndex + 1}</h2>

            <div className="modal-search">
              <input
                type="text"
                value={tmdbSearchQuery}
                onChange={e => setTmdbSearchQuery(e.target.value)}
                onKeyPress={e => e.key === 'Enter' && searchTMDB()}
                placeholder="Wyszukaj w TMDB..."
                autoFocus
              />
              <button onClick={searchTMDB} disabled={tmdbSearching}>
                {tmdbSearching ? '...' : '🔍'}
              </button>
            </div>

            <div className="modal-results">
              {tmdbResults.map(movie => (
                <div
                  key={movie.id}
                  className="result-item"
                  onClick={() => selectTmdbMovie(movie)}
                >
                  {movie.poster_path ? (
                    <img src={`${TMDB_IMAGE_BASE}/w92${movie.poster_path}`} alt={movie.title} />
                  ) : (
                    <div className="no-poster-small">🎬</div>
                  )}
                  <div className="result-info">
                    <span className="result-title">{movie.title}</span>
                    <span className="result-year">{movie.release_date?.substring(0, 4)}</span>
                    <span className="result-rating">⭐ {movie.vote_average?.toFixed(1)}</span>
                  </div>
                </div>
              ))}
            </div>

            <button className="modal-close" onClick={() => setTmdbModal({ open: false, slotIndex: null })}>
              ✕ Zamknij
            </button>
          </div>
        </div>
      )}

      {/* Movie Preview Modal - Full Details */}
      {previewModal.open && (
        <div className="modal-overlay preview-overlay" onClick={() => setPreviewModal({ open: false, movie: null, slotIndex: null })}>
          <div className="preview-modal" onClick={e => e.stopPropagation()}>
            {previewLoading ? (
              <div className="preview-loading">
                <div className="spinner"></div>
                <p>Ładowanie danych z TMDB...</p>
              </div>
            ) : previewData ? (
              <>
                {/* Backdrop header */}
                <div className="preview-header">
                  {previewData.backdrop_url && (
                    <img src={previewData.backdrop_url} alt={previewData.title} className="preview-backdrop" />
                  )}
                  <div className="preview-header-overlay">
                    {previewData.logo_url ? (
                      <img src={previewData.logo_url} alt="Logo" className="preview-logo" />
                    ) : (
                      <h2 className="preview-title-fallback">{previewData.title}</h2>
                    )}
                  </div>
                  <button
                    className="preview-close"
                    onClick={() => setPreviewModal({ open: false, movie: null, slotIndex: null })}
                  >
                    ✕
                  </button>
                </div>

                {/* Main content */}
                <div className="preview-body">
                  <div className="preview-main">
                    {/* Poster */}
                    <div className="preview-poster">
                      {previewData.poster_url ? (
                        <img src={previewData.poster_url} alt={previewData.title} />
                      ) : (
                        <div className="no-poster">🎬</div>
                      )}
                    </div>

                    {/* Info */}
                    <div className="preview-info">
                      <h2>{previewData.title}</h2>

                      <div className="preview-meta">
                        {previewData.tmdb_details?.release_date && (
                          <span className="meta-item">📅 {previewData.tmdb_details.release_date.substring(0, 4)}</span>
                        )}
                        {previewData.tmdb_details?.runtime && (
                          <span className="meta-item">⏱️ {previewData.tmdb_details.runtime} min</span>
                        )}
                        {previewData.tmdb_details?.vote_average && (
                          <span className="meta-item rating">⭐ {previewData.tmdb_details.vote_average.toFixed(1)}</span>
                        )}
                      </div>

                      {/* Genres */}
                      {previewData.tmdb_details?.genres?.length > 0 && (
                        <div className="preview-genres">
                          {previewData.tmdb_details.genres.map(g => (
                            <span key={g.id} className="genre-tag">{g.name}</span>
                          ))}
                        </div>
                      )}

                      {/* Directors */}
                      {previewData.tmdb_directors?.length > 0 && (
                        <p className="preview-directors">
                          <strong>Reżyseria:</strong> {previewData.tmdb_directors.join(', ')}
                        </p>
                      )}

                      {/* Short description (from our DB) */}
                      {previewData.short_description && (
                        <div className="preview-short-desc">
                          <strong>Krótki opis:</strong>
                          <p>{previewData.short_description}</p>
                        </div>
                      )}

                      {/* TMDB Overview */}
                      {previewData.tmdb_details?.overview && (
                        <div className="preview-overview">
                          <strong>Opis TMDB:</strong>
                          <p>{previewData.tmdb_details.overview}</p>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Cast */}
                  {previewData.tmdb_cast?.length > 0 && (
                    <div className="preview-section">
                      <h3>🎭 Obsada</h3>
                      <div className="preview-cast">
                        {previewData.tmdb_cast.map((actor, i) => (
                          <div key={i} className="cast-item">
                            {actor.profile_path ? (
                              <img src={`${TMDB_IMAGE_BASE}/w92${actor.profile_path}`} alt={actor.name} />
                            ) : (
                              <div className="cast-no-photo">👤</div>
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

                  {/* Videos/Trailers */}
                  {previewData.tmdb_videos?.length > 0 && (
                    <div className="preview-section">
                      <h3>🎬 Trailery</h3>
                      <div className="preview-videos">
                        {previewData.tmdb_videos.map((video, i) => (
                          <a
                            key={i}
                            href={`https://www.youtube.com/watch?v=${video.key}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="video-item"
                          >
                            <img
                              src={`https://img.youtube.com/vi/${video.key}/mqdefault.jpg`}
                              alt={video.name}
                            />
                            <span className="video-play">▶</span>
                            <span className="video-name">{video.name}</span>
                          </a>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Posters — klikalne, wybór plakata */}
                  {previewData.tmdb_posters?.length > 0 && (
                    <div className="preview-section">
                      <h3>🎞️ Plakat (kliknij aby wybrać)</h3>
                      <div className="preview-backdrops">
                        {previewData.tmdb_posters.map((p, i) => {
                          const fullUrl = `${TMDB_IMAGE_BASE}/original${p.file_path}`;
                          const isPicked = selectedPosterUrl === fullUrl;
                          return (
                            <img
                              key={i}
                              src={`${TMDB_IMAGE_BASE}/w185${p.file_path}`}
                              alt={`Poster ${i + 1}`}
                              onClick={() => setSelectedPosterUrl(fullUrl)}
                              style={{
                                cursor: 'pointer',
                                outline: isPicked ? '4px solid #5FEDD4' : 'none',
                                opacity: isPicked ? 1 : 0.65,
                                transition: 'all 0.15s'
                              }}
                            />
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Backdrops — klikalne, wybór tła */}
                  {previewData.tmdb_backdrops?.length > 0 && (
                    <div className="preview-section">
                      <h3>🖼️ Zdjęcie tła (kliknij aby wybrać)</h3>
                      <div className="preview-backdrops">
                        {previewData.tmdb_backdrops.map((img, i) => {
                          const fullUrl = `${TMDB_IMAGE_BASE}/original${img.file_path}`;
                          const isPicked = selectedBackdropUrl === fullUrl;
                          return (
                            <img
                              key={i}
                              src={`${TMDB_IMAGE_BASE}/w500${img.file_path}`}
                              alt={`Backdrop ${i + 1}`}
                              onClick={() => setSelectedBackdropUrl(fullUrl)}
                              style={{
                                cursor: 'pointer',
                                outline: isPicked ? '4px solid #5FEDD4' : 'none',
                                opacity: isPicked ? 1 : 0.65,
                                transition: 'all 0.15s'
                              }}
                            />
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Logos — klikalne, wybór logo */}
                  {previewData.tmdb_logos?.length > 0 && (
                    <div className="preview-section">
                      <h3>✨ Logo (kliknij aby wybrać)</h3>
                      <div className="preview-logos">
                        {previewData.tmdb_logos.map((logo, i) => {
                          const fullUrl = `${TMDB_IMAGE_BASE}/original${logo.file_path}`;
                          const isPicked = selectedLogoUrl === fullUrl;
                          return (
                            <img
                              key={i}
                              src={`${TMDB_IMAGE_BASE}/w300${logo.file_path}`}
                              alt={`Logo ${i + 1}`}
                              onClick={() => setSelectedLogoUrl(fullUrl)}
                              style={{
                                cursor: 'pointer',
                                outline: isPicked ? '4px solid #5FEDD4' : 'none',
                                opacity: isPicked ? 1 : 0.65,
                                transition: 'all 0.15s'
                              }}
                            />
                          );
                        })}
                      </div>
                    </div>
                  )}

                  {/* Trailer URL — używany w sliderze hero */}
                  <div className="preview-section">
                    <h3>🎥 Link do zwiastuna (DASH .smil / .mpd / MP4 / YouTube)</h3>
                    <input
                      type="text"
                      value={selectedTrailerUrl}
                      onChange={e => setSelectedTrailerUrl(e.target.value)}
                      placeholder="np. https://n-1411-3.dcs.redcdn.pl/dash/.../dash.smil"
                      style={{
                        width: '100%', padding: '10px 14px', fontSize: 14,
                        background: '#0d0d0d', color: '#eee', border: '1px solid #444', borderRadius: 6
                      }}
                    />
                    <div style={{ fontSize: 12, color: '#888', marginTop: 4 }}>
                      Trailer odtwarzany w sliderze Kino Play po fokusie. Aktualnie: {selectedTrailerUrl ? '✓' : '— brak'}
                    </div>
                  </div>
                </div>

                {/* Action buttons */}
                <div className="preview-actions">
                  <button
                    className="preview-btn cancel"
                    onClick={() => setPreviewModal({ open: false, movie: null, slotIndex: null })}
                  >
                    ← Wróć
                  </button>
                  <button
                    className="preview-btn confirm"
                    onClick={confirmMovieSelection}
                  >
                    ✓ Wybierz do Slotu {previewModal.slotIndex + 1}
                  </button>
                </div>
              </>
            ) : (
              <p>Brak danych</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default Slider;
