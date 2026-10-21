import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Spinner } from '../components/ui'
import { errorMessage } from '../lib/api'

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/projects" replace />

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (password.length < 10) {
      setError('La contraseña debe tener al menos 10 caracteres.')
      return
    }

    setSubmitting(true)
    try {
      await register(email.trim(), password, fullName.trim())
      navigate('/projects', { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-shell">
      <aside className="auth-story auth-story--register">
        <div className="brand brand--light">
          <span className="brand__mark">D</span>
          <span>
            <strong>DevTrack</strong>
            <small>Proyectos y tareas</small>
          </span>
        </div>
        <div className="auth-story__content">
          <p className="eyebrow">Empieza en menos de un minuto</p>
          <h1>
            Tu próximo
            <br />
            proyecto empieza aquí.
          </h1>
          <p>
            Crea tu cuenta, monta un proyecto y reparte el trabajo. DevTrack se encarga de que
            cada persona vea exactamente lo que le toca.
          </p>
          <div className="auth-quote">
            <span>“</span>
            <p>La claridad también es una funcionalidad.</p>
          </div>
        </div>
        <p className="auth-story__foot">Sin tarjeta · Sin instalaciones · v1</p>
      </aside>

      <main className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">Crear cuenta</p>
          <h2>Únete a DevTrack</h2>
          <p className="muted">Solo necesitamos lo básico para empezar.</p>

          <form className="form-stack" onSubmit={onSubmit}>
            {error && <div className="alert alert--error">{error}</div>}

            <label className="field">
              <span>Nombre completo</span>
              <input
                type="text"
                autoComplete="name"
                required
                maxLength={120}
                placeholder="Ada Lovelace"
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
              />
            </label>

            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="email"
                required
                maxLength={320}
                placeholder="tu@equipo.dev"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>

            <label className="field">
              <span>Contraseña</span>
              <input
                type="password"
                autoComplete="new-password"
                required
                minLength={10}
                maxLength={72}
                placeholder="Mínimo 10 caracteres"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <small>Entre 10 y 72 caracteres.</small>
            </label>

            <button type="submit" className="button button--primary button--block" disabled={submitting}>
              {submitting && <Spinner size="sm" />}
              Crear cuenta y entrar
            </button>
          </form>

          <p className="auth-switch">
            Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
          </p>
        </div>
      </main>
    </div>
  )
}
