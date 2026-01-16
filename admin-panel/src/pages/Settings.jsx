import React, { useState, useEffect } from 'react';
import { supabase } from '../supabase';

function Settings() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  // Form state
  const [sliderAutoRotateInterval, setSliderAutoRotateInterval] = useState(5);
  const [sliderPauseAfterInteraction, setSliderPauseAfterInteraction] = useState(10);

  useEffect(() => {
    fetchConfig();
  }, []);

  const fetchConfig = async () => {
    try {
      setLoading(true);
      // Pobierz z tabeli app_config (ta sama co TV app)
      const { data, error } = await supabase
        .from('app_config')
        .select('slider_auto_rotate_interval_ms, slider_pause_after_interaction_ms')
        .eq('id', 1)
        .single();

      if (error && error.code !== 'PGRST116') {
        console.log('Error fetching config:', error);
      }

      if (data) {
        // Convert ms to seconds for display
        if (data.slider_auto_rotate_interval_ms) {
          setSliderAutoRotateInterval(data.slider_auto_rotate_interval_ms / 1000);
        }
        if (data.slider_pause_after_interaction_ms) {
          setSliderPauseAfterInteraction(data.slider_pause_after_interaction_ms / 1000);
        }
      }
    } catch (error) {
      console.error('Error fetching config:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      setMessage({ type: '', text: '' });

      const intervalMs = Math.round(sliderAutoRotateInterval * 1000);
      const pauseMs = Math.round(sliderPauseAfterInteraction * 1000);

      // Update app_config table using direct fetch
      const response = await fetch(
        'https://kexrkaqxoadxugnnbnjh.supabase.co/rest/v1/app_config?id=eq.1',
        {
          method: 'PATCH',
          headers: {
            'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo',
            'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2NTM1OTIxOSwiZXhwIjoyMDgwOTM1MjE5fQ.nUhtr4cX_l3LtZy0QOPfdZD2zEY_OV3rmxAxetci2oo',
            'Content-Type': 'application/json',
            'Prefer': 'return=representation'
          },
          body: JSON.stringify({
            slider_auto_rotate_interval_ms: intervalMs,
            slider_pause_after_interaction_ms: pauseMs
          })
        }
      );

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Blad zapisu');
      }

      setMessage({ type: 'success', text: `Zapisano! Rotacja: ${sliderAutoRotateInterval}s, Pauza: ${sliderPauseAfterInteraction}s. Kliknij "Pobierz parametry" w TV.` });

      // Refresh config
      fetchConfig();
    } catch (error) {
      console.error('Error saving config:', error);
      setMessage({ type: 'error', text: 'Blad zapisywania: ' + error.message });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="loading">Ladowanie ustawien...</div>;
  }

  return (
    <div className="settings-page">
      <h1>Ustawienia Aplikacji</h1>

      {message.text && (
        <div className={`message ${message.type}`}>
          {message.text}
        </div>
      )}

      <div className="settings-section">
        <h2>Slider ODKRYWAJ - Auto-rotacja</h2>
        <p className="section-description">
          Ustawienia automatycznej rotacji slidera na stronie ODKRYWAJ
        </p>

        <div className="setting-item">
          <label htmlFor="autoRotateInterval">
            Czas auto-rotacji (sekundy)
          </label>
          <div className="setting-input-group">
            <input
              type="number"
              id="autoRotateInterval"
              min="1"
              max="60"
              step="0.5"
              value={sliderAutoRotateInterval}
              onChange={(e) => setSliderAutoRotateInterval(parseFloat(e.target.value) || 5)}
              className="setting-input"
            />
            <span className="setting-unit">sek</span>
          </div>
          <p className="setting-hint">
            Slider automatycznie przesunie sie na nastepny slajd po tym czasie.
          </p>
        </div>

        <div className="setting-item">
          <label htmlFor="pauseAfterInteraction">
            Czas pauzy po interakcji (sekundy)
          </label>
          <div className="setting-input-group">
            <input
              type="number"
              id="pauseAfterInteraction"
              min="1"
              max="120"
              step="1"
              value={sliderPauseAfterInteraction}
              onChange={(e) => setSliderPauseAfterInteraction(parseFloat(e.target.value) || 10)}
              className="setting-input"
            />
            <span className="setting-unit">sek</span>
          </div>
          <p className="setting-hint">
            Po kliknieciu LEFT/RIGHT, auto-rotacja pauzuje na ten czas.
            Podczas pauzy wyswietla sie bullet zamiast paska postepu.
          </p>
        </div>

        <div className="setting-preview">
          <h4>Podglad animacji</h4>
          <div className="preview-bar-container">
            <div
              className="preview-bar"
              style={{
                animation: `progressFill ${sliderAutoRotateInterval}s linear infinite`
              }}
            />
          </div>
          <p className="preview-text">
            Rotacja: {sliderAutoRotateInterval}s | Pauza: {sliderPauseAfterInteraction}s
          </p>
        </div>
      </div>

      <div className="settings-actions">
        <button
          onClick={handleSave}
          disabled={saving}
          className="save-button"
        >
          {saving ? 'Zapisywanie...' : 'Zapisz ustawienia'}
        </button>
      </div>

      <style>{`
        .settings-page {
          padding: 20px;
          max-width: 800px;
          min-height: 100vh;
          background: #1a1a2e;
        }

        .settings-page h1 {
          color: #fff;
          margin-bottom: 30px;
        }

        .loading {
          color: #fff;
          padding: 40px;
          background: #1a1a2e;
          min-height: 100vh;
        }

        .message {
          padding: 12px 16px;
          border-radius: 8px;
          margin-bottom: 20px;
          font-weight: 500;
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

        .settings-section {
          background: rgba(255, 255, 255, 0.05);
          border-radius: 12px;
          padding: 24px;
          margin-bottom: 24px;
        }

        .settings-section h2 {
          color: #5FEDD4;
          font-size: 1.3rem;
          margin-bottom: 8px;
        }

        .section-description {
          color: rgba(255, 255, 255, 0.6);
          margin-bottom: 24px;
        }

        .setting-item {
          margin-bottom: 24px;
        }

        .setting-item label {
          display: block;
          color: #fff;
          font-weight: 500;
          margin-bottom: 8px;
        }

        .setting-input-group {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .setting-input {
          width: 120px;
          padding: 12px 16px;
          font-size: 1.2rem;
          border: 2px solid rgba(255, 255, 255, 0.2);
          border-radius: 8px;
          background: rgba(0, 0, 0, 0.3);
          color: #fff;
          text-align: center;
        }

        .setting-input:focus {
          outline: none;
          border-color: #5FEDD4;
        }

        .setting-unit {
          color: rgba(255, 255, 255, 0.6);
          font-size: 1rem;
        }

        .setting-hint {
          color: rgba(255, 255, 255, 0.5);
          font-size: 0.85rem;
          margin-top: 8px;
        }

        .setting-preview {
          background: rgba(0, 0, 0, 0.2);
          border-radius: 8px;
          padding: 16px;
          margin-top: 16px;
        }

        .setting-preview h4 {
          color: rgba(255, 255, 255, 0.7);
          margin-bottom: 12px;
          font-size: 0.9rem;
        }

        .preview-bar-container {
          width: 200px;
          height: 12px;
          background: rgba(255, 255, 255, 0.2);
          border-radius: 6px;
          overflow: hidden;
        }

        .preview-bar {
          height: 100%;
          background: #5FEDD4;
          width: 0%;
          border-radius: 6px;
        }

        @keyframes progressFill {
          0% { width: 0%; }
          100% { width: 100%; }
        }

        .preview-text {
          color: rgba(255, 255, 255, 0.5);
          font-size: 0.8rem;
          margin-top: 8px;
        }

        .settings-actions {
          margin-top: 32px;
        }

        .save-button {
          background: #5FEDD4;
          color: #1a1a2e;
          border: none;
          padding: 14px 32px;
          font-size: 1rem;
          font-weight: 600;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s;
        }

        .save-button:hover {
          background: #4dd4c0;
          transform: translateY(-1px);
        }

        .save-button:disabled {
          opacity: 0.6;
          cursor: not-allowed;
          transform: none;
        }
      `}</style>
    </div>
  );
}

export default Settings;
