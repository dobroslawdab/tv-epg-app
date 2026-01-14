import React from 'react';
import { NavLink } from 'react-router-dom';

function Sidebar({ onLogout }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <h2>TV Admin</h2>
        <span className="subtitle">Kino Play Manager</span>
      </div>

      <nav className="sidebar-nav">
        <NavLink to="/" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
          <span className="nav-icon">📊</span>
          Dashboard
        </NavLink>

        <NavLink to="/homepage" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
          <span className="nav-icon">🏠</span>
          Strona Główna
        </NavLink>

        <NavLink to="/movies" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
          <span className="nav-icon">🎬</span>
          Movies
        </NavLink>

        <NavLink to="/slider" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
          <span className="nav-icon">🖼️</span>
          Slider
        </NavLink>

        <NavLink to="/colors" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
          <span className="nav-icon">🎨</span>
          Colors
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <button onClick={onLogout} className="logout-button">
          <span className="nav-icon">🚪</span>
          Logout
        </button>
      </div>
    </aside>
  );
}

export default Sidebar;
