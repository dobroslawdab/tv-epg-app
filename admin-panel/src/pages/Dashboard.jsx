import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { supabase } from '../supabase';

function Dashboard() {
  const [stats, setStats] = useState({
    totalMovies: 0,
    activeMovies: 0,
    inactiveMovies: 0,
    genres: [],
    loading: true,
  });

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      // Get all movies count
      const { count: totalMovies } = await supabase
        .from('movies')
        .select('*', { count: 'exact', head: true });

      // Get active movies count
      const { count: activeMovies } = await supabase
        .from('movies')
        .select('*', { count: 'exact', head: true })
        .eq('is_active', true);

      // Get genres
      const { data: genreData } = await supabase
        .from('movies')
        .select('genre')
        .not('genre', 'is', null);

      const genreCounts = {};
      genreData?.forEach((m) => {
        if (m.genre) {
          genreCounts[m.genre] = (genreCounts[m.genre] || 0) + 1;
        }
      });

      const genres = Object.entries(genreCounts)
        .map(([name, count]) => ({ name, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10);

      setStats({
        totalMovies: totalMovies || 0,
        activeMovies: activeMovies || 0,
        inactiveMovies: (totalMovies || 0) - (activeMovies || 0),
        genres,
        loading: false,
      });
    } catch (error) {
      console.error('Error fetching stats:', error);
      setStats((prev) => ({ ...prev, loading: false }));
    }
  };

  if (stats.loading) {
    return <div className="loading">Loading dashboard...</div>;
  }

  return (
    <div className="dashboard">
      <h1>Dashboard</h1>

      <div className="stats-grid">
        <div className="stat-card">
          <h3>Total Movies</h3>
          <p className="stat-number">{stats.totalMovies}</p>
        </div>

        <div className="stat-card active">
          <h3>Active</h3>
          <p className="stat-number">{stats.activeMovies}</p>
        </div>

        <div className="stat-card inactive">
          <h3>Inactive</h3>
          <p className="stat-number">{stats.inactiveMovies}</p>
        </div>
      </div>

      <div className="dashboard-sections">
        <div className="section">
          <h2>Top Genres</h2>
          <div className="genre-list">
            {stats.genres.map((genre) => (
              <div key={genre.name} className="genre-item">
                <span className="genre-name">{genre.name}</span>
                <span className="genre-count">{genre.count}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="section">
          <h2>Quick Actions</h2>
          <div className="quick-actions">
            <Link to="/movies" className="action-button">
              Manage Movies
            </Link>
            <Link to="/colors" className="action-button">
              Edit Colors
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Dashboard;
