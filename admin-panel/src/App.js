import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { supabase } from './supabase';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Movies from './pages/Movies';
import Colors from './pages/Colors';
import MovieDetails from './pages/MovieDetails';
import Slider from './pages/Slider';
import OdkrywajSlider from './pages/OdkrywajSlider';
import VodContent from './pages/VodContent';
import HomepageEditor from './pages/HomepageEditor';
import Settings from './pages/Settings';
import Sidebar from './components/Sidebar';
import './App.css';

function App() {
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check current session
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setLoading(false);
    });

    // Listen for auth changes
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
    });

    return () => subscription.unsubscribe();
  }, []);

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner"></div>
        <p>Loading...</p>
      </div>
    );
  }

  if (!session) {
    return <Login />;
  }

  return (
    <Router>
      <div className="app-layout">
        <Sidebar onLogout={() => supabase.auth.signOut()} />
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/movies" element={<Movies />} />
            <Route path="/movies/:id" element={<MovieDetails />} />
            <Route path="/colors" element={<Colors />} />
            <Route path="/slider" element={<Slider />} />
            <Route path="/odkrywaj-slider" element={<OdkrywajSlider />} />
            <Route path="/vod-content" element={<VodContent />} />
            <Route path="/homepage" element={<HomepageEditor />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;
