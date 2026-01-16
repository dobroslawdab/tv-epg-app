import React, { useState, useEffect } from 'react';
import { supabase } from '../supabase';

// VOD data from JSON (embedded for easy access)
const VOD_JSON_DATA = [
  {
    "tytul": "Grzechy i grzeszki",
    "kategoria": "Serial obyczajowy",
    "opis": "Dwaj księża konfrontują się w konfesjonałach z nietypowymi osobami.",
    "logo_kanalu": "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/24/images/874819430?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0",
    "miniaturka_programu": "https://r.dcs.redcdn.pl/scale/play/playtv/images/epg_play_sndf49nbfkU_r320B7F65DlnbfsEFcaB2h4o6bJ79X/m/e/diaakpa3463116.jpg?srcmode=3&srcw=16&srch=9&dstw=1920&dsth=1080&quality=95&type=1",
    "link": "https://playnow.pl/wideo/serial/18375205"
  }
];

function VodContent() {
  const [vodItems, setVodItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [categories, setCategories] = useState([]);
  const [dataSource, setDataSource] = useState('database'); // 'database' or 'json'
  const [jsonData, setJsonData] = useState([]);

  useEffect(() => {
    loadJsonData();
    fetchVodFromDatabase();
  }, []);

  const loadJsonData = async () => {
    try {
      // Try to fetch from the JSON file hosted locally or use embedded data
      const response = await fetch('/vod_data.json');
      if (response.ok) {
        const data = await response.json();
        setJsonData(data);
        // Extract unique categories
        const uniqueCategories = [...new Set(data.map(item => item.kategoria))].filter(Boolean).sort();
        setCategories(prev => [...new Set([...prev, ...uniqueCategories])]);
      }
    } catch (error) {
      console.log('Could not load JSON file, using embedded data');
      // Use embedded sample data
      setJsonData(VOD_JSON_DATA);
    }
  };

  const fetchVodFromDatabase = async () => {
    try {
      setLoading(true);
      const { data, error } = await supabase
        .from('vod_content')
        .select('*')
        .eq('is_active', true)
        .order('title');

      if (error) {
        // Table might not exist yet
        console.log('vod_content table not found or error:', error.message);
        setVodItems([]);
        setDataSource('json');
        return;
      }

      setVodItems(data || []);

      // Extract categories from database
      if (data && data.length > 0) {
        const dbCategories = [...new Set(data.map(item => item.category))].filter(Boolean);
        setCategories(prev => [...new Set([...prev, ...dbCategories])].sort());
      }
    } catch (error) {
      console.error('Error fetching VOD:', error);
      setDataSource('json');
    } finally {
      setLoading(false);
    }
  };

  const importJsonToDatabase = async () => {
    if (jsonData.length === 0) {
      setMessage({ type: 'error', text: 'Brak danych JSON do importu' });
      return;
    }

    setImporting(true);
    setMessage({ type: '', text: '' });

    try {
      // Transform JSON data to database format
      const transformedData = jsonData.map(item => ({
        title: item.tytul,
        category: item.kategoria,
        description: item.opis,
        channel_logo_url: item.logo_kanalu,
        thumbnail_url: item.miniaturka_programu,
        link: item.link,
        source: 'json_import',
        is_active: true
      }));

      // Insert in batches of 50
      const batchSize = 50;
      let imported = 0;

      for (let i = 0; i < transformedData.length; i += batchSize) {
        const batch = transformedData.slice(i, i + batchSize);
        const { error } = await supabase
          .from('vod_content')
          .upsert(batch, {
            onConflict: 'title',
            ignoreDuplicates: true
          });

        if (error) {
          console.error('Batch error:', error);
          // Try inserting one by one for this batch
          for (const item of batch) {
            const { error: singleError } = await supabase
              .from('vod_content')
              .insert(item);
            if (!singleError) imported++;
          }
        } else {
          imported += batch.length;
        }
      }

      setMessage({
        type: 'success',
        text: `Zaimportowano ${imported} z ${transformedData.length} pozycji`
      });

      // Refresh from database
      await fetchVodFromDatabase();
      setDataSource('database');
    } catch (error) {
      console.error('Import error:', error);
      setMessage({ type: 'error', text: `Błąd importu: ${error.message}` });
    } finally {
      setImporting(false);
    }
  };

  const deleteVodItem = async (id) => {
    if (!window.confirm('Czy na pewno usunąć ten materiał?')) return;

    try {
      const { error } = await supabase
        .from('vod_content')
        .update({ is_active: false })
        .eq('id', id);

      if (error) throw error;

      setMessage({ type: 'success', text: 'Materiał usunięty!' });
      fetchVodFromDatabase();
    } catch (error) {
      console.error('Error deleting:', error);
      setMessage({ type: 'error', text: `Błąd usuwania: ${error.message}` });
    }
  };

  // Get current display data based on source
  const displayData = dataSource === 'database'
    ? vodItems
    : jsonData.map((item, index) => ({
        id: `json-${index}`,
        title: item.tytul,
        category: item.kategoria,
        description: item.opis,
        channel_logo_url: item.logo_kanalu,
        thumbnail_url: item.miniaturka_programu,
        link: item.link
      }));

  // Filter data
  const filteredData = displayData.filter(item => {
    const matchesSearch = !searchQuery ||
      item.title?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.description?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = !selectedCategory || item.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  if (loading) {
    return <div className="loading">Ładowanie...</div>;
  }

  return (
    <div className="vod-content-page">
      <h1>Materiały Wideo (VOD)</h1>
      <p className="page-description">
        Przeglądaj i zarządzaj materiałami wideo. Możesz zaimportować dane z pliku JSON do bazy danych.
      </p>

      {message.text && (
        <div className={`message ${message.type}`}>
          {message.text}
          <button onClick={() => setMessage({ type: '', text: '' })}>x</button>
        </div>
      )}

      {/* Data source toggle & import */}
      <div className="controls-bar">
        <div className="source-toggle">
          <span>Źródło danych:</span>
          <button
            className={dataSource === 'database' ? 'active' : ''}
            onClick={() => setDataSource('database')}
          >
            Baza danych ({vodItems.length})
          </button>
          <button
            className={dataSource === 'json' ? 'active' : ''}
            onClick={() => setDataSource('json')}
          >
            Plik JSON ({jsonData.length})
          </button>
        </div>

        {dataSource === 'json' && jsonData.length > 0 && (
          <button
            className="btn-import"
            onClick={importJsonToDatabase}
            disabled={importing}
          >
            {importing ? 'Importowanie...' : `Importuj ${jsonData.length} pozycji do bazy`}
          </button>
        )}
      </div>

      {/* Search and filters */}
      <div className="filters-bar">
        <input
          type="text"
          placeholder="Szukaj po tytule lub opisie..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="search-input"
        />

        <select
          value={selectedCategory}
          onChange={(e) => setSelectedCategory(e.target.value)}
          className="category-select"
        >
          <option value="">Wszystkie kategorie</option>
          {categories.map(cat => (
            <option key={cat} value={cat}>{cat}</option>
          ))}
        </select>

        <span className="results-count">
          Wyników: {filteredData.length}
        </span>
      </div>

      {/* VOD list */}
      <div className="vod-grid">
        {filteredData.map(item => (
          <div key={item.id} className="vod-card">
            <div className="vod-thumbnail">
              {item.thumbnail_url && (
                <img src={item.thumbnail_url} alt={item.title} />
              )}
              {item.channel_logo_url && (
                <img
                  src={item.channel_logo_url}
                  alt="Channel"
                  className="channel-logo"
                />
              )}
            </div>
            <div className="vod-info">
              <h3>{item.title}</h3>
              <span className="vod-category">{item.category}</span>
              <p className="vod-description">{item.description}</p>
              {item.link && (
                <a href={item.link} target="_blank" rel="noopener noreferrer" className="vod-link">
                  Zobacz na PlayNow
                </a>
              )}
            </div>
            {dataSource === 'database' && (
              <div className="vod-actions">
                <button
                  className="btn-delete"
                  onClick={() => deleteVodItem(item.id)}
                >
                  Usuń
                </button>
              </div>
            )}
          </div>
        ))}

        {filteredData.length === 0 && (
          <div className="no-results">
            <p>Brak materiałów do wyświetlenia</p>
            {dataSource === 'database' && jsonData.length > 0 && (
              <p>Przełącz na "Plik JSON" i zaimportuj dane do bazy.</p>
            )}
          </div>
        )}
      </div>

      <style>{`
        .vod-content-page {
          padding: 20px;
          max-width: 1400px;
          background: #1a1a2e;
          min-height: 100vh;
        }

        .vod-content-page h1 {
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

        .controls-bar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 20px;
          padding: 16px;
          background: rgba(255,255,255,0.05);
          border-radius: 12px;
          flex-wrap: wrap;
          gap: 16px;
        }

        .source-toggle {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .source-toggle span {
          color: rgba(255,255,255,0.7);
        }

        .source-toggle button {
          padding: 8px 16px;
          background: rgba(255,255,255,0.1);
          border: 1px solid transparent;
          border-radius: 6px;
          color: #fff;
          cursor: pointer;
          transition: all 0.2s;
        }

        .source-toggle button.active {
          background: rgba(95, 237, 212, 0.2);
          border-color: #5FEDD4;
          color: #5FEDD4;
        }

        .btn-import {
          padding: 10px 20px;
          background: #5FEDD4;
          border: none;
          border-radius: 8px;
          color: #1a1a2e;
          font-weight: 600;
          cursor: pointer;
          transition: all 0.2s;
        }

        .btn-import:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(95, 237, 212, 0.3);
        }

        .btn-import:disabled {
          opacity: 0.6;
          cursor: not-allowed;
          transform: none;
        }

        .filters-bar {
          display: flex;
          gap: 16px;
          margin-bottom: 24px;
          align-items: center;
          flex-wrap: wrap;
        }

        .search-input {
          flex: 1;
          min-width: 250px;
          padding: 12px 16px;
          background: rgba(0,0,0,0.3);
          border: 1px solid rgba(255,255,255,0.2);
          border-radius: 8px;
          color: #fff;
          font-size: 14px;
        }

        .search-input:focus {
          outline: none;
          border-color: #5FEDD4;
        }

        .category-select {
          padding: 12px 16px;
          background: rgba(0,0,0,0.3);
          border: 1px solid rgba(255,255,255,0.2);
          border-radius: 8px;
          color: #fff;
          font-size: 14px;
          min-width: 200px;
        }

        .category-select:focus {
          outline: none;
          border-color: #5FEDD4;
        }

        .results-count {
          color: rgba(255,255,255,0.6);
          font-size: 14px;
        }

        .vod-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
          gap: 20px;
        }

        .vod-card {
          background: rgba(255,255,255,0.05);
          border-radius: 12px;
          overflow: hidden;
          transition: transform 0.2s, box-shadow 0.2s;
        }

        .vod-card:hover {
          transform: translateY(-4px);
          box-shadow: 0 8px 24px rgba(0,0,0,0.3);
        }

        .vod-thumbnail {
          position: relative;
          width: 100%;
          height: 200px;
          background: rgba(0,0,0,0.3);
        }

        .vod-thumbnail img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .vod-thumbnail .channel-logo {
          position: absolute;
          bottom: 8px;
          right: 8px;
          width: 48px;
          height: 48px;
          border-radius: 8px;
          object-fit: cover;
          background: rgba(0,0,0,0.5);
        }

        .vod-info {
          padding: 16px;
        }

        .vod-info h3 {
          color: #fff;
          margin: 0 0 8px 0;
          font-size: 16px;
          line-height: 1.3;
        }

        .vod-category {
          display: inline-block;
          padding: 4px 10px;
          background: rgba(95, 237, 212, 0.2);
          color: #5FEDD4;
          border-radius: 4px;
          font-size: 12px;
          margin-bottom: 12px;
        }

        .vod-description {
          color: rgba(255,255,255,0.6);
          font-size: 13px;
          line-height: 1.5;
          margin: 0 0 12px 0;
          display: -webkit-box;
          -webkit-line-clamp: 3;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .vod-link {
          color: #5FEDD4;
          text-decoration: none;
          font-size: 13px;
        }

        .vod-link:hover {
          text-decoration: underline;
        }

        .vod-actions {
          padding: 12px 16px;
          border-top: 1px solid rgba(255,255,255,0.1);
          display: flex;
          gap: 8px;
        }

        .btn-delete {
          padding: 6px 12px;
          background: #ff6464;
          border: none;
          border-radius: 6px;
          color: #fff;
          font-size: 13px;
          cursor: pointer;
        }

        .no-results {
          grid-column: 1 / -1;
          text-align: center;
          padding: 60px 20px;
          color: rgba(255,255,255,0.5);
        }

        .no-results p {
          margin: 8px 0;
        }
      `}</style>
    </div>
  );
}

export default VodContent;
