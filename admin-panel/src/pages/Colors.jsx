import React, { useState, useEffect } from 'react';
import { supabase } from '../supabase';

const COLOR_FIELDS = [
  { key: 'color_background', label: 'Background', description: 'Main app background' },
  { key: 'color_background_secondary', label: 'Background Secondary', description: 'Secondary background color' },
  { key: 'color_focus', label: 'Focus', description: 'Focus/accent color (aqua)' },
  { key: 'color_focus_glow', label: 'Focus Glow', description: 'Focus glow effect (with opacity)' },
  { key: 'color_text_primary', label: 'Text Primary', description: 'Primary text color' },
  { key: 'color_text_secondary', label: 'Text Secondary', description: 'Secondary text color' },
  { key: 'color_selected_bg', label: 'Selected Background', description: 'Selected item background' },
  { key: 'color_selected_text', label: 'Selected Text', description: 'Selected item text' },
  { key: 'color_item_bg', label: 'Item Background', description: 'Menu item background' },
  { key: 'color_container_bg', label: 'Container Background', description: 'Container background' },
];

function Colors() {
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    fetchConfig();
  }, []);

  const fetchConfig = async () => {
    const { data, error } = await supabase
      .from('app_config')
      .select('*')
      .order('updated_at', { ascending: false })
      .limit(1)
      .single();

    if (error) {
      console.error('Error fetching config:', error);
      // Create default config if not exists
      setConfig({
        color_background: '#281443',
        color_background_secondary: '#48227C',
        color_focus: '#5FEDD4',
        color_focus_glow: '#4D5FEDD4',
        color_text_primary: '#EEEEEE',
        color_text_secondary: '#CCEEEEEE',
        color_selected_bg: '#FFFFFF',
        color_selected_text: '#48227C',
        color_item_bg: '#66000000',
        color_container_bg: '#1AEEEEEE',
      });
    } else {
      setConfig(data);
    }
    setLoading(false);
  };

  const handleColorChange = (key, value) => {
    setConfig({ ...config, [key]: value });
  };

  const saveConfig = async () => {
    setSaving(true);
    setMessage('');

    const updateData = {};
    COLOR_FIELDS.forEach((field) => {
      updateData[field.key] = config[field.key];
    });

    const { error } = await supabase
      .from('app_config')
      .update(updateData)
      .eq('id', config.id);

    if (error) {
      setMessage('Error saving: ' + error.message);
    } else {
      setMessage('Colors saved successfully!');
      setTimeout(() => setMessage(''), 3000);
    }

    setSaving(false);
  };

  if (loading) {
    return <div className="loading">Loading colors...</div>;
  }

  return (
    <div className="colors-page">
      <div className="colors-header">
        <h1>App Colors</h1>
        <button
          onClick={saveConfig}
          disabled={saving}
          className="save-button"
        >
          {saving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>

      {message && (
        <div className={`message ${message.includes('Error') ? 'error' : 'success'}`}>
          {message}
        </div>
      )}

      <div className="colors-grid">
        {COLOR_FIELDS.map((field) => (
          <div key={field.key} className="color-item">
            <div className="color-info">
              <label>{field.label}</label>
              <span className="color-description">{field.description}</span>
            </div>
            <div className="color-inputs">
              <input
                type="color"
                value={config[field.key]?.slice(0, 7) || '#000000'}
                onChange={(e) => handleColorChange(field.key, e.target.value)}
                className="color-picker"
              />
              <input
                type="text"
                value={config[field.key] || ''}
                onChange={(e) => handleColorChange(field.key, e.target.value)}
                className="color-hex"
                placeholder="#RRGGBB"
              />
            </div>
            <div
              className="color-preview"
              style={{ backgroundColor: config[field.key] }}
            />
          </div>
        ))}
      </div>

      <div className="preview-section">
        <h2>Live Preview</h2>
        <div
          className="preview-box"
          style={{ backgroundColor: config.color_background }}
        >
          <div
            className="preview-container"
            style={{ backgroundColor: config.color_container_bg }}
          >
            <div
              className="preview-item"
              style={{ backgroundColor: config.color_item_bg }}
            >
              <span style={{ color: config.color_text_primary }}>Menu Item</span>
            </div>
            <div
              className="preview-item focused"
              style={{ backgroundColor: config.color_focus }}
            >
              <span style={{ color: config.color_selected_text }}>Focused Item</span>
            </div>
            <div
              className="preview-item selected"
              style={{ backgroundColor: config.color_selected_bg }}
            >
              <span style={{ color: config.color_selected_text }}>Selected Item</span>
            </div>
          </div>
          <p style={{ color: config.color_text_secondary, marginTop: '20px' }}>
            Secondary text preview
          </p>
        </div>
      </div>
    </div>
  );
}

export default Colors;
