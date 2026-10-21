import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { initials } from '../lib/format'

export function Layout() {
  const { user, logout } = useAuth()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/projects" className="brand" aria-label="DevTrack, ir a proyectos">
          <span className="brand__mark">D</span>
          <span>
            <strong>DevTrack</strong>
            <small>Proyectos y tareas</small>
          </span>
        </NavLink>

        <nav className="sidebar__nav" aria-label="Navegacion principal">
          <NavLink to="/projects" className={({ isActive }) => (isActive ? 'nav-link is-active' : 'nav-link')}>
            <span className="nav-link__icon" aria-hidden="true">
              ▦
            </span>
            Proyectos
          </NavLink>
        </nav>

        <div className="sidebar__footer">
          {user && (
            <div
              className="user-card"
              title={`${user.fullName} · ${user.email} · ID #${user.id}`}
            >
              <span className="avatar">{initials(user.fullName)}</span>
              <span className="user-card__meta">
                <strong>{user.fullName}</strong>
                <small>{user.email} · #{user.id}</small>
              </span>
            </div>
          )}
          <button type="button" className="nav-link nav-link--button" onClick={logout}>
            <span className="nav-link__icon" aria-hidden="true">
              ↪
            </span>
            Cerrar sesión
          </button>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
