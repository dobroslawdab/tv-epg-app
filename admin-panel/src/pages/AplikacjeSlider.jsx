import React, { useState, useEffect } from 'react';
import { supabase } from '../supabase';

function AplikacjeSlider() {
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  // Modal states
  const [addModal, setAddModal] = useState({ open: false, slotIndex: null });
  const [editModal, setEditModal] = useState({ open: false, item: null });
  const [movieSearchModal, setMovieSearchModal] = useState({ open: false });
  const [vodSearchModal, setVodSearchModal] = useState({ open: false });

  // Search states
  const [movieSearchQuery, setMovieSearchQuery] = useState('');
  const [movieSearchResults, setMovieSearchResults] = useState([]);
  const [vodSearchQuery, setVodSearchQuery] = useState('');
  const [vodSearchResults, setVodSearchResults] = useState([]);

  // Form state for new/edit slide
  const [formData, setFormData] = useState({
    source_type: 'manual',
    source_movie_id: null,
    source_vod_id: null,
    title: '',
    title_max_lines: 2,
    description: '',
    show_metadata: true,
    metadata_text: '',
    backdrop_url: '',
    logo_url: '',
    button_type: 'open_app',
    button_custom_text: '',
    youtube_url: ''
  });

  // Upload states
  const [uploadingBackdrop, setUploadingBackdrop] = useState(false);
  const [uploadingLogo, setUploadingLogo] = useState(false);

  useEffect(() => {
    fetchSlots();
  }, []);

  const fetchSlots = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('aplikacje_slider')
        .select('*')
        .eq('is_active', true)
        .order('slot_order');

      if (error) throw error;
      setSlots(data || []);
    } catch (error) {
      console.error('Error fetching slots:', error);
      setMessage({ type: 'error', text: 'Błąd ładowania slajdów' });
    } finally {
      setLoading(false);
    }
  };

  const searchMovies = async (query) => {
    if (!query.trim()) {
      setMovieSearchResults([]);
      return;
    }
    try {
      const { data, error } = await supabase
        .from('movies')
        .select('*')
        .ilike('title', `%${query}%`)
        .eq('is_active', true)
        .limit(20);

      if (error) throw error;
      setMovieSearchResults(data || []);
    } catch (error) {
      console.error('Error searching movies:', error);
    }
  };

  const searchVodContent = async (query) => {
    if (!query.trim()) {
      setVodSearchResults([]);
      return;
    }
    try {
      const { data, error } = await supabase
        .from('vod_content')
        .select('*')
        .ilike('title', `%${query}%`)
        .eq('is_active', true)
        .limit(20);

      if (error) throw error;
      setVodSearchResults(data || []);
    } catch (error) {
      console.error('Error searching VOD:', error);
    }
  };

  // Logo Kino Play dla filmów z bazy
  const KINO_PLAY_LOGO = 'https://kexrkaqxoadxugnnbnjh.supabase.co/storage/v1/object/public/aplikacje-slider/kino_play_logo.png';

  const selectMovie = (movie) => {
    // Formatuj cenę - jeśli jest cena w filmie, pokaż ją
    const priceText = movie.price ? `${movie.price} zł` : '';

    setFormData({
      ...formData,
      source_type: 'movie',
      source_movie_id: movie.id,
      source_vod_id: null,
      title: movie.title,
      description: movie.short_description || movie.description || '',
      metadata_text: `${movie.release_year || ''} | ${movie.genre || ''} | ${movie.runtime || ''}`.replace(/\| \|/g, '|').replace(/^\| /, '').replace(/ \|$/, ''),
      backdrop_url: movie.backdrop_url || '',
      // Użyj logo Kino Play dla filmów
      logo_url: movie.logo_url || KINO_PLAY_LOGO,
      youtube_url: movie.youtube_url || '',
      button_type: 'open_app',
      // Zapisz cenę w custom text jeśli istnieje
      button_custom_text: priceText
    });
    setMovieSearchModal({ open: false });
  };

  const selectVod = (vod) => {
    setFormData({
      ...formData,
      source_type: 'vod_json',
      source_movie_id: null,
      source_vod_id: vod.id,
      title: vod.title,
      description: vod.description || '',
      metadata_text: vod.category || '',
      backdrop_url: vod.thumbnail_url || '',
      // Logo kanału dla VOD
      logo_url: vod.channel_logo_url || '',
      youtube_url: '',
      button_type: 'install_app'
    });
    setVodSearchModal({ open: false });
  };

  const uploadImage = async (file, type) => {
    const setUploading = type === 'backdrop' ? setUploadingBackdrop : setUploadingLogo;
    setUploading(true);

    try {
      const fileName = `${type}_${Date.now()}_${file.name.replace(/[^a-zA-Z0-9.-]/g, '_')}`;

      // Próba uploadu do Supabase Storage
      const { data: uploadData, error: uploadError } = await supabase.storage
        .from('aplikacje-slider')
        .upload(fileName, file, {
          cacheControl: '3600',
          upsert: false
        });

      if (uploadError) {
        // Jeśli bucket nie istnieje lub brak uprawnień, pokaż szczegółowy błąd
        console.error('Upload error details:', uploadError);

        if (uploadError.message.includes('bucket') || uploadError.statusCode === '404') {
          throw new Error('Bucket "aplikacje-slider" nie istnieje. Utwórz go w Supabase Storage.');
        }
        if (uploadError.message.includes('policy') || uploadError.statusCode === '403') {
          throw new Error('Brak uprawnień do uploadu. Sprawdź polityki bucketa w Supabase.');
        }
        throw uploadError;
      }

      // Pobierz publiczny URL
      const { data: urlData } = supabase.storage
        .from('aplikacje-slider')
        .getPublicUrl(fileName);

      if (!urlData?.publicUrl) {
        throw new Error('Nie udało się uzyskać publicznego URL dla pliku');
      }

      setFormData(prev => ({
        ...prev,
        [type === 'backdrop' ? 'backdrop_url' : 'logo_url']: urlData.publicUrl
      }));

      setMessage({ type: 'success', text: `${type === 'backdrop' ? 'Tło' : 'Logo'} przesłane!` });
    } catch (error) {
      console.error('Upload error:', error);
      setMessage({ type: 'error', text: `Błąd przesyłania: ${error.message}. Możesz też wkleić URL obrazka bezpośrednio.` });
    } finally {
      setUploading(false);
    }
  };

  // Helper to check if string is valid UUID
  const isValidUUID = (str) => {
    if (!str) return false;
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    return uuidRegex.test(str);
  };

  const saveSlide = async () => {
    if (!formData.title.trim()) {
      setMessage({ type: 'error', text: 'Tytuł jest wymagany' });
      return;
    }
    if (!formData.backdrop_url.trim()) {
      setMessage({ type: 'error', text: 'Obrazek tła jest wymagany' });
      return;
    }

    setSaving(true);
    try {
      const nextOrder = slots.length + 1;

      // Only include source_movie_id if it's a valid UUID, otherwise set to null
      // (movies table might use numeric IDs, but aplikacje_slider expects UUID)
      const sourceMovieId = isValidUUID(formData.source_movie_id) ? formData.source_movie_id : null;
      const sourceVodId = isValidUUID(formData.source_vod_id) ? formData.source_vod_id : null;

      const { error } = await supabase
        .from('aplikacje_slider')
        .insert({
          slot_order: nextOrder,
          source_type: formData.source_type,
          source_movie_id: sourceMovieId,
          source_vod_id: sourceVodId,
          title: formData.title,
          title_max_lines: formData.title_max_lines,
          description: formData.description,
          show_metadata: formData.show_metadata,
          metadata_text: formData.metadata_text,
          backdrop_url: formData.backdrop_url,
          logo_url: formData.logo_url,
          button_type: formData.button_type,
          button_custom_text: formData.button_custom_text,
          youtube_url: formData.youtube_url,
          is_active: true
        });

      if (error) throw error;

      setMessage({ type: 'success', text: 'Slajd dodany!' });
      setAddModal({ open: false, slotIndex: null });
      resetForm();
      fetchSlots();
    } catch (error) {
      console.error('Error saving:', error);
      setMessage({ type: 'error', text: `Błąd zapisu: ${error.message}` });
    } finally {
      setSaving(false);
    }
  };

  const updateSlide = async () => {
    if (!editModal.item) return;

    setSaving(true);
    try {
      const { error } = await supabase
        .from('aplikacje_slider')
        .update({
          title: formData.title,
          title_max_lines: formData.title_max_lines,
          description: formData.description,
          show_metadata: formData.show_metadata,
          metadata_text: formData.metadata_text,
          backdrop_url: formData.backdrop_url,
          logo_url: formData.logo_url,
          button_type: formData.button_type,
          button_custom_text: formData.button_custom_text,
          youtube_url: formData.youtube_url,
          updated_at: new Date().toISOString()
        })
        .eq('id', editModal.item.id);

      if (error) throw error;

      setMessage({ type: 'success', text: 'Slajd zaktualizowany!' });
      setEditModal({ open: false, item: null });
      resetForm();
      fetchSlots();
    } catch (error) {
      console.error('Error updating:', error);
      setMessage({ type: 'error', text: `Błąd aktualizacji: ${error.message}` });
    } finally {
      setSaving(false);
    }
  };

  const deleteSlide = async (id) => {
    if (!window.confirm('Czy na pewno usunąć ten slajd?')) return;

    try {
      const { error } = await supabase
        .from('aplikacje_slider')
        .update({ is_active: false })
        .eq('id', id);

      if (error) throw error;

      setMessage({ type: 'success', text: 'Slajd usunięty!' });
      fetchSlots();
    } catch (error) {
      console.error('Error deleting:', error);
      setMessage({ type: 'error', text: `Błąd usuwania: ${error.message}` });
    }
  };

  const moveSlide = async (index, direction) => {
    const newIndex = direction === 'up' ? index - 1 : index + 1;
    if (newIndex < 0 || newIndex >= slots.length) return;

    const newSlots = [...slots];
    [newSlots[index], newSlots[newIndex]] = [newSlots[newIndex], newSlots[index]];

    // Update slot_order for both items
    try {
      await supabase
        .from('aplikacje_slider')
        .update({ slot_order: newIndex + 1 })
        .eq('id', slots[index].id);

      await supabase
        .from('aplikacje_slider')
        .update({ slot_order: index + 1 })
        .eq('id', slots[newIndex].id);

      fetchSlots();
    } catch (error) {
      console.error('Error moving:', error);
    }
  };

  const openEditModal = (item) => {
    setFormData({
      source_type: item.source_type,
      source_movie_id: item.source_movie_id,
      source_vod_id: item.source_vod_id,
      title: item.title,
      title_max_lines: item.title_max_lines || 2,
      description: item.description || '',
      show_metadata: item.show_metadata !== false,
      metadata_text: item.metadata_text || '',
      backdrop_url: item.backdrop_url || '',
      logo_url: item.logo_url || '',
      button_type: item.button_type || 'open_app',
      button_custom_text: item.button_custom_text || '',
      youtube_url: item.youtube_url || ''
    });
    setEditModal({ open: true, item });
  };

  const resetForm = () => {
    setFormData({
      source_type: 'manual',
      source_movie_id: null,
      source_vod_id: null,
      title: '',
      title_max_lines: 2,
      description: '',
      show_metadata: true,
      metadata_text: '',
      backdrop_url: '',
      logo_url: '',
      button_type: 'open_app',
      button_custom_text: '',
      youtube_url: ''
    });
  };

  const getSourceLabel = (type) => {
    switch (type) {
      case 'movie': return 'Film z bazy';
      case 'vod_json': return 'Materiał wideo';
      case 'manual': return 'Ręczny';
      default: return type;
    }
  };

  const getButtonLabel = (type, customText) => {
    switch (type) {
      case 'open_app': return 'Otwórz aplikację';
      case 'install_app': return 'Zainstaluj aplikację';
      case 'custom': return customText || 'Własny';
      default: return type;
    }
  };

  if (loading) {
    return <div className="loading">Ładowanie...</div>;
  }

  return (
    <div className="aplikacje-slider-page">
      <h1>APLIKACJE Slider - Zarządzanie</h1>
      <p className="page-description">
        Zarządzaj banerami wyświetlanymi w hero sliderze zakładki APLIKACJE. Każdy slajd zawiera ilustrację (backdrop), logo aplikacji, tytuł, opis oraz przycisk akcji (np. "Otwórz aplikację" / "Zainstaluj aplikację").
      </p>

      {message.text && (
        <div className={`message ${message.type}`}>
          {message.text}
          <button onClick={() => setMessage({ type: '', text: '' })}>×</button>
        </div>
      )}

      {/* Lista slotów */}
      <div className="slots-list">
        {slots.map((slot, index) => (
          <div key={slot.id} className="slot-card">
            <div className="slot-number">{index + 1}</div>
            <div className="slot-preview">
              {slot.backdrop_url && (
                <img src={slot.backdrop_url} alt={slot.title} />
              )}
            </div>
            <div className="slot-info">
              <h3>{slot.title}</h3>
              <p className="slot-meta">
                Źródło: <strong>{getSourceLabel(slot.source_type)}</strong> |
                Przycisk: <strong>{getButtonLabel(slot.button_type, slot.button_custom_text)}</strong>
              </p>
              {slot.metadata_text && (
                <p className="slot-metadata">{slot.metadata_text}</p>
              )}
            </div>
            <div className="slot-actions">
              <button onClick={() => openEditModal(slot)} className="btn-edit">Edytuj</button>
              <button onClick={() => deleteSlide(slot.id)} className="btn-delete">Usuń</button>
              <button
                onClick={() => moveSlide(index, 'up')}
                disabled={index === 0}
                className="btn-move"
              >↑</button>
              <button
                onClick={() => moveSlide(index, 'down')}
                disabled={index === slots.length - 1}
                className="btn-move"
              >↓</button>
            </div>
          </div>
        ))}

        {/* Przycisk dodawania */}
        {slots.length < 10 && (
          <div className="add-slot-card" onClick={() => setAddModal({ open: true, slotIndex: slots.length })}>
            <div className="add-icon">+</div>
            <p>Dodaj slajd ({slots.length}/10)</p>
          </div>
        )}
      </div>

      {/* Modal dodawania */}
      {addModal.open && (
        <div className="modal-overlay" onClick={() => { setAddModal({ open: false, slotIndex: null }); resetForm(); }}>
          <div className="modal-content large" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Dodaj slajd do APLIKACJE</h2>
              <button className="close-btn" onClick={() => { setAddModal({ open: false, slotIndex: null }); resetForm(); }}>×</button>
            </div>

            <div className="modal-body">
              {/* Wybór źródła */}
              <div className="form-section">
                <label>Źródło:</label>
                <div className="source-buttons">
                  <button
                    className={`source-btn ${formData.source_type === 'movie' ? 'active' : ''}`}
                    onClick={() => setMovieSearchModal({ open: true })}
                  >
                    🎬 Film z bazy
                  </button>
                  <button
                    className={`source-btn ${formData.source_type === 'vod_json' ? 'active' : ''}`}
                    onClick={() => setVodSearchModal({ open: true })}
                  >
                    📺 Materiał wideo
                  </button>
                  <button
                    className={`source-btn ${formData.source_type === 'manual' ? 'active' : ''}`}
                    onClick={() => setFormData({ ...formData, source_type: 'manual', source_movie_id: null, source_vod_id: null })}
                  >
                    ✏️ Ręcznie
                  </button>
                </div>
              </div>

              {/* Tytuł */}
              <div className="form-section">
                <label>Tytuł:</label>
                <textarea
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  placeholder="Wpisz tytuł..."
                  rows={3}
                />
                <div className="title-lines">
                  <span>Maks. linii:</span>
                  {[1, 2, 3].map(n => (
                    <button
                      key={n}
                      className={formData.title_max_lines === n ? 'active' : ''}
                      onClick={() => setFormData({ ...formData, title_max_lines: n })}
                    >{n}</button>
                  ))}
                </div>
              </div>

              {/* Opis */}
              <div className="form-section">
                <label>Opis:</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder="Wpisz opis..."
                  rows={4}
                />
              </div>

              {/* Metadane */}
              <div className="form-section">
                <label>
                  <input
                    type="checkbox"
                    checked={formData.show_metadata}
                    onChange={(e) => setFormData({ ...formData, show_metadata: e.target.checked })}
                  />
                  Pokaż metadane
                </label>
                {formData.show_metadata && (
                  <input
                    type="text"
                    value={formData.metadata_text}
                    onChange={(e) => setFormData({ ...formData, metadata_text: e.target.value })}
                    placeholder="np. 2024 | Dramat | 120 min"
                  />
                )}
              </div>

              {/* Przycisk */}
              <div className="form-section">
                <label>Przycisk:</label>
                <div className="button-options">
                  <label>
                    <input
                      type="radio"
                      name="button_type"
                      checked={formData.button_type === 'open_app'}
                      onChange={() => setFormData({ ...formData, button_type: 'open_app' })}
                    />
                    📂 Otwórz aplikację
                  </label>
                  <label>
                    <input
                      type="radio"
                      name="button_type"
                      checked={formData.button_type === 'install_app'}
                      onChange={() => setFormData({ ...formData, button_type: 'install_app' })}
                    />
                    ⬇️ Zainstaluj aplikację
                  </label>
                  <label>
                    <input
                      type="radio"
                      name="button_type"
                      checked={formData.button_type === 'custom'}
                      onChange={() => setFormData({ ...formData, button_type: 'custom' })}
                    />
                    Własny tekst:
                    <input
                      type="text"
                      value={formData.button_custom_text}
                      onChange={(e) => setFormData({ ...formData, button_custom_text: e.target.value })}
                      placeholder="Wpisz tekst..."
                      disabled={formData.button_type !== 'custom'}
                      style={{ marginLeft: '8px', width: '150px' }}
                    />
                  </label>
                </div>
              </div>

              {/* Obrazy */}
              <div className="form-section">
                <label>Obrazek tła (backdrop):</label>
                <div className="image-input">
                  <input
                    type="text"
                    value={formData.backdrop_url}
                    onChange={(e) => setFormData({ ...formData, backdrop_url: e.target.value })}
                    placeholder="URL obrazka..."
                  />
                  <span>lub</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => e.target.files[0] && uploadImage(e.target.files[0], 'backdrop')}
                    disabled={uploadingBackdrop}
                  />
                  {uploadingBackdrop && <span className="uploading">Przesyłanie...</span>}
                </div>
                {formData.backdrop_url && (
                  <div className="image-preview">
                    <img src={formData.backdrop_url} alt="Backdrop preview" />
                  </div>
                )}
              </div>

              <div className="form-section">
                <label>Logo (opcjonalne):</label>
                <div className="image-input">
                  <input
                    type="text"
                    value={formData.logo_url}
                    onChange={(e) => setFormData({ ...formData, logo_url: e.target.value })}
                    placeholder="URL logo..."
                  />
                  <span>lub</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => e.target.files[0] && uploadImage(e.target.files[0], 'logo')}
                    disabled={uploadingLogo}
                  />
                  {uploadingLogo && <span className="uploading">Przesyłanie...</span>}
                </div>
                {formData.logo_url && (
                  <div className="image-preview small">
                    <img src={formData.logo_url} alt="Logo preview" />
                  </div>
                )}
              </div>

              {/* YouTube */}
              <div className="form-section">
                <label>Trailer YouTube (opcjonalne):</label>
                <input
                  type="text"
                  value={formData.youtube_url}
                  onChange={(e) => setFormData({ ...formData, youtube_url: e.target.value })}
                  placeholder="https://youtube.com/watch?v=..."
                />
              </div>
            </div>

            <div className="modal-footer">
              <button onClick={() => { setAddModal({ open: false, slotIndex: null }); resetForm(); }} className="btn-cancel">
                Anuluj
              </button>
              <button onClick={saveSlide} disabled={saving} className="btn-save">
                {saving ? 'Zapisywanie...' : 'Zapisz'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal edycji */}
      {editModal.open && (
        <div className="modal-overlay" onClick={() => { setEditModal({ open: false, item: null }); resetForm(); }}>
          <div className="modal-content large" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Edytuj slajd</h2>
              <button className="close-btn" onClick={() => { setEditModal({ open: false, item: null }); resetForm(); }}>×</button>
            </div>

            <div className="modal-body">
              {/* Same form fields as add modal */}
              <div className="form-section">
                <label>Tytuł:</label>
                <textarea
                  value={formData.title}
                  onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                  rows={3}
                />
                <div className="title-lines">
                  <span>Maks. linii:</span>
                  {[1, 2, 3].map(n => (
                    <button
                      key={n}
                      className={formData.title_max_lines === n ? 'active' : ''}
                      onClick={() => setFormData({ ...formData, title_max_lines: n })}
                    >{n}</button>
                  ))}
                </div>
              </div>

              <div className="form-section">
                <label>Opis:</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  rows={4}
                />
              </div>

              <div className="form-section">
                <label>
                  <input
                    type="checkbox"
                    checked={formData.show_metadata}
                    onChange={(e) => setFormData({ ...formData, show_metadata: e.target.checked })}
                  />
                  Pokaż metadane
                </label>
                {formData.show_metadata && (
                  <input
                    type="text"
                    value={formData.metadata_text}
                    onChange={(e) => setFormData({ ...formData, metadata_text: e.target.value })}
                    placeholder="np. 2024 | Dramat | 120 min"
                  />
                )}
              </div>

              <div className="form-section">
                <label>Przycisk:</label>
                <div className="button-options">
                  <label>
                    <input
                      type="radio"
                      name="edit_button_type"
                      checked={formData.button_type === 'open_app'}
                      onChange={() => setFormData({ ...formData, button_type: 'open_app' })}
                    />
                    📂 Otwórz aplikację
                  </label>
                  <label>
                    <input
                      type="radio"
                      name="edit_button_type"
                      checked={formData.button_type === 'install_app'}
                      onChange={() => setFormData({ ...formData, button_type: 'install_app' })}
                    />
                    ⬇️ Zainstaluj aplikację
                  </label>
                  <label>
                    <input
                      type="radio"
                      name="edit_button_type"
                      checked={formData.button_type === 'custom'}
                      onChange={() => setFormData({ ...formData, button_type: 'custom' })}
                    />
                    Własny:
                    <input
                      type="text"
                      value={formData.button_custom_text}
                      onChange={(e) => setFormData({ ...formData, button_custom_text: e.target.value })}
                      disabled={formData.button_type !== 'custom'}
                      style={{ marginLeft: '8px', width: '150px' }}
                    />
                  </label>
                </div>
              </div>

              <div className="form-section">
                <label>Obrazek tła:</label>
                <div className="image-input">
                  <input
                    type="text"
                    value={formData.backdrop_url}
                    onChange={(e) => setFormData({ ...formData, backdrop_url: e.target.value })}
                  />
                  <span>lub</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => e.target.files[0] && uploadImage(e.target.files[0], 'backdrop')}
                    disabled={uploadingBackdrop}
                  />
                </div>
                {formData.backdrop_url && (
                  <div className="image-preview">
                    <img src={formData.backdrop_url} alt="Backdrop" />
                  </div>
                )}
              </div>

              <div className="form-section">
                <label>Logo:</label>
                <div className="image-input">
                  <input
                    type="text"
                    value={formData.logo_url}
                    onChange={(e) => setFormData({ ...formData, logo_url: e.target.value })}
                  />
                  <span>lub</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => e.target.files[0] && uploadImage(e.target.files[0], 'logo')}
                    disabled={uploadingLogo}
                  />
                </div>
                {formData.logo_url && (
                  <div className="image-preview small">
                    <img src={formData.logo_url} alt="Logo" />
                  </div>
                )}
              </div>

              <div className="form-section">
                <label>Trailer YouTube:</label>
                <input
                  type="text"
                  value={formData.youtube_url}
                  onChange={(e) => setFormData({ ...formData, youtube_url: e.target.value })}
                />
              </div>
            </div>

            <div className="modal-footer">
              <button onClick={() => { setEditModal({ open: false, item: null }); resetForm(); }} className="btn-cancel">
                Anuluj
              </button>
              <button onClick={updateSlide} disabled={saving} className="btn-save">
                {saving ? 'Zapisywanie...' : 'Zapisz zmiany'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal wyszukiwania filmów */}
      {movieSearchModal.open && (
        <div className="modal-overlay" onClick={() => setMovieSearchModal({ open: false })}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Wybierz film z bazy</h2>
              <button className="close-btn" onClick={() => setMovieSearchModal({ open: false })}>×</button>
            </div>
            <div className="modal-body">
              <input
                type="text"
                value={movieSearchQuery}
                onChange={(e) => {
                  setMovieSearchQuery(e.target.value);
                  searchMovies(e.target.value);
                }}
                placeholder="Szukaj filmu..."
                autoFocus
              />
              <div className="search-results">
                {movieSearchResults.map(movie => (
                  <div key={movie.id} className="search-item" onClick={() => selectMovie(movie)}>
                    {movie.poster_url && <img src={movie.poster_url} alt={movie.title} />}
                    <div>
                      <strong>{movie.title}</strong>
                      <span>{movie.release_year} | {movie.genre}</span>
                    </div>
                  </div>
                ))}
                {movieSearchQuery && movieSearchResults.length === 0 && (
                  <p className="no-results">Nie znaleziono filmów</p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Modal wyszukiwania VOD */}
      {vodSearchModal.open && (
        <div className="modal-overlay" onClick={() => setVodSearchModal({ open: false })}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Wybierz materiał wideo</h2>
              <button className="close-btn" onClick={() => setVodSearchModal({ open: false })}>×</button>
            </div>
            <div className="modal-body">
              <input
                type="text"
                value={vodSearchQuery}
                onChange={(e) => {
                  setVodSearchQuery(e.target.value);
                  searchVodContent(e.target.value);
                }}
                placeholder="Szukaj materiału..."
                autoFocus
              />
              <div className="search-results">
                {vodSearchResults.map(vod => (
                  <div key={vod.id} className="search-item" onClick={() => selectVod(vod)}>
                    {vod.thumbnail_url && <img src={vod.thumbnail_url} alt={vod.title} />}
                    <div>
                      <strong>{vod.title}</strong>
                      <span>{vod.category}</span>
                    </div>
                  </div>
                ))}
                {vodSearchQuery && vodSearchResults.length === 0 && (
                  <p className="no-results">Nie znaleziono materiałów. Możesz je zaimportować w zakładce "Materiały Wideo".</p>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      <style>{`
        .aplikacje-slider-page {
          padding: 20px;
          max-width: 1200px;
          background: #1a1a2e;
          min-height: 100vh;
        }

        .aplikacje-slider-page h1 {
          color: #fff;
          margin-bottom: 8px;
        }

        .page-description {
          color: rgba(255,255,255,0.6);
          margin-bottom: 24px;
        }

        .loading {
          color: #fff;
          padding: 40px;
          text-align: center;
        }

        .message {
          padding: 12px 16px;
          border-radius: 8px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: center;
        }

        .message.success {
          background: rgba(95, 237, 212, 0.2);
          color: #5FEDD4;
          border: 1px solid #5FEDD4;
        }

        .message.error {
          background: rgba(255, 100, 100, 0.2);
          color: #ff6464;
          border: 1px solid #ff6464;
        }

        .message button {
          background: none;
          border: none;
          color: inherit;
          font-size: 20px;
          cursor: pointer;
        }

        .slots-list {
          display: flex;
          flex-direction: column;
          gap: 16px;
        }

        .slot-card {
          background: rgba(255,255,255,0.05);
          border-radius: 12px;
          padding: 16px;
          display: flex;
          align-items: center;
          gap: 16px;
        }

        .slot-number {
          width: 40px;
          height: 40px;
          background: #5FEDD4;
          color: #1a1a2e;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: bold;
          flex-shrink: 0;
        }

        .slot-preview {
          width: 160px;
          height: 90px;
          background: rgba(0,0,0,0.3);
          border-radius: 8px;
          overflow: hidden;
          flex-shrink: 0;
        }

        .slot-preview img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .slot-info {
          flex: 1;
        }

        .slot-info h3 {
          color: #fff;
          margin: 0 0 4px 0;
          font-size: 16px;
        }

        .slot-meta {
          color: rgba(255,255,255,0.6);
          font-size: 13px;
          margin: 0 0 4px 0;
        }

        .slot-metadata {
          color: rgba(255,255,255,0.4);
          font-size: 12px;
          margin: 0;
        }

        .slot-actions {
          display: flex;
          gap: 8px;
        }

        .slot-actions button {
          padding: 8px 12px;
          border: none;
          border-radius: 6px;
          cursor: pointer;
          font-size: 13px;
        }

        .btn-edit {
          background: #5FEDD4;
          color: #1a1a2e;
        }

        .btn-delete {
          background: #ff6464;
          color: #fff;
        }

        .btn-move {
          background: rgba(255,255,255,0.1);
          color: #fff;
        }

        .btn-move:disabled {
          opacity: 0.3;
          cursor: not-allowed;
        }

        .add-slot-card {
          background: rgba(95, 237, 212, 0.1);
          border: 2px dashed #5FEDD4;
          border-radius: 12px;
          padding: 32px;
          text-align: center;
          cursor: pointer;
          transition: all 0.2s;
        }

        .add-slot-card:hover {
          background: rgba(95, 237, 212, 0.2);
        }

        .add-icon {
          font-size: 48px;
          color: #5FEDD4;
        }

        .add-slot-card p {
          color: #5FEDD4;
          margin: 8px 0 0 0;
        }

        /* Modal styles */
        .modal-overlay {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: rgba(0,0,0,0.8);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 1000;
        }

        .modal-content {
          background: #1a1a2e;
          border-radius: 16px;
          width: 90%;
          max-width: 500px;
          max-height: 90vh;
          overflow-y: auto;
        }

        .modal-content.large {
          max-width: 700px;
        }

        .modal-header {
          padding: 20px;
          border-bottom: 1px solid rgba(255,255,255,0.1);
          display: flex;
          justify-content: space-between;
          align-items: center;
        }

        .modal-header h2 {
          color: #fff;
          margin: 0;
          font-size: 20px;
        }

        .close-btn {
          background: none;
          border: none;
          color: #fff;
          font-size: 24px;
          cursor: pointer;
        }

        .modal-body {
          padding: 20px;
        }

        .modal-footer {
          padding: 20px;
          border-top: 1px solid rgba(255,255,255,0.1);
          display: flex;
          justify-content: flex-end;
          gap: 12px;
        }

        .form-section {
          margin-bottom: 20px;
        }

        .form-section > label {
          display: block;
          color: #fff;
          margin-bottom: 8px;
          font-weight: 500;
        }

        .form-section input[type="text"],
        .form-section textarea {
          width: 100%;
          padding: 12px;
          background: rgba(0,0,0,0.3);
          border: 1px solid rgba(255,255,255,0.2);
          border-radius: 8px;
          color: #fff;
          font-size: 14px;
        }

        .form-section textarea {
          resize: vertical;
        }

        .form-section input:focus,
        .form-section textarea:focus {
          outline: none;
          border-color: #5FEDD4;
        }

        .source-buttons {
          display: flex;
          gap: 12px;
          flex-wrap: wrap;
        }

        .source-btn {
          padding: 12px 20px;
          background: rgba(255,255,255,0.1);
          border: 2px solid transparent;
          border-radius: 8px;
          color: #fff;
          cursor: pointer;
          transition: all 0.2s;
        }

        .source-btn:hover {
          background: rgba(255,255,255,0.15);
        }

        .source-btn.active {
          border-color: #5FEDD4;
          background: rgba(95, 237, 212, 0.2);
        }

        .title-lines {
          display: flex;
          align-items: center;
          gap: 8px;
          margin-top: 8px;
        }

        .title-lines span {
          color: rgba(255,255,255,0.6);
          font-size: 13px;
        }

        .title-lines button {
          width: 32px;
          height: 32px;
          border: 1px solid rgba(255,255,255,0.2);
          background: transparent;
          color: #fff;
          border-radius: 4px;
          cursor: pointer;
        }

        .title-lines button.active {
          background: #5FEDD4;
          color: #1a1a2e;
          border-color: #5FEDD4;
        }

        .button-options {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }

        .button-options label {
          display: flex;
          align-items: center;
          gap: 8px;
          color: #fff;
          cursor: pointer;
        }

        .image-input {
          display: flex;
          align-items: center;
          gap: 12px;
          flex-wrap: wrap;
        }

        .image-input input[type="text"] {
          flex: 1;
          min-width: 200px;
        }

        .image-input span {
          color: rgba(255,255,255,0.5);
        }

        .image-input input[type="file"] {
          color: #fff;
        }

        .uploading {
          color: #5FEDD4;
          font-size: 13px;
        }

        .image-preview {
          margin-top: 12px;
          border-radius: 8px;
          overflow: hidden;
          max-width: 100%;
        }

        .image-preview img {
          max-width: 100%;
          max-height: 200px;
          object-fit: contain;
        }

        .image-preview.small img {
          max-height: 80px;
        }

        .btn-cancel {
          padding: 12px 24px;
          background: rgba(255,255,255,0.1);
          border: none;
          border-radius: 8px;
          color: #fff;
          cursor: pointer;
        }

        .btn-save {
          padding: 12px 24px;
          background: #5FEDD4;
          border: none;
          border-radius: 8px;
          color: #1a1a2e;
          font-weight: 600;
          cursor: pointer;
        }

        .btn-save:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .search-results {
          margin-top: 16px;
          max-height: 400px;
          overflow-y: auto;
        }

        .search-item {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 12px;
          background: rgba(255,255,255,0.05);
          border-radius: 8px;
          margin-bottom: 8px;
          cursor: pointer;
          transition: background 0.2s;
        }

        .search-item:hover {
          background: rgba(255,255,255,0.1);
        }

        .search-item img {
          width: 60px;
          height: 90px;
          object-fit: cover;
          border-radius: 4px;
        }

        .search-item div {
          flex: 1;
        }

        .search-item strong {
          display: block;
          color: #fff;
          margin-bottom: 4px;
        }

        .search-item span {
          color: rgba(255,255,255,0.6);
          font-size: 13px;
        }

        .no-results {
          color: rgba(255,255,255,0.5);
          text-align: center;
          padding: 20px;
        }
      `}</style>
    </div>
  );
}

export default AplikacjeSlider;
