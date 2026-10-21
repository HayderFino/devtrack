import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Spinner } from '../components/ui'
import { errorMessage } from '../lib/api'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/projects" replace />

  const from = (location.state as { from?: string } | null)?.from

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(from && from !== '/login' ? from : '/projects', { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-shell">
      <aside className="auth-story">
        <div className="brand brand--light">
          <span className="brand__mark">D</span>
          <span>
            <strong>DevTrack</strong>
            <small>Proyectos y tareas</small>
          </span>
        </div>
        <div className="auth-story__content">
          <p className="eyebrow">Tu equipo, en la misma página</p>
          <h1>
            Menos ruido.
            <br />
            Más entregas.
          </h1>
          <p>
            Organiza proyectos, reparte responsabilidades y mueve cada tarea hasta el final.
            Todo en un espacio compartido y con permisos claros.
          </p>
          <ul className="auth-points">
            <li>Roles de propietario, editor y lector</li>
            <li>Tareas con prioridad, responsable y fecha límite</li>
            <li>Historial de cambios con control de version</li>
          </ul>
        </div>
        <p className="auth-story__foot">DevTrack v1 · Sprint 5</p>
      </aside>

      <main className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">Bienvenido de nuevo</p>
          <h2>Inicia sesión</h2>
          <p className="muted">Accede para continuar con tus proyectos.</p>

          <form className="form-stack" onSubmit={onSubmit}>
            {error && <div className="alert alert--error">{error}</div>}

            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="email"
                required
                placeholder="tu@equipo.dev"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            <label className="field">
              <span>Contraseña</span>
              <input
                type="password"
                autoComplete="current-password"
                required
                placeholder="••••••••••"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>

            <button type="submit" className="button button--primary button--block" disabled={submitting}>
              {submitting && <Spinner size="sm" />}
              Entrar
            </button>
          </form>

          <p className="auth-switch">
            Aún no tienes cuenta? <Link to="/register">Crear cuenta</Link>
          </p>
        </div>
      </main>
    </div>
  )
}
