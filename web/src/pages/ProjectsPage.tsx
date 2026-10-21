import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ProjectFormModal } from '../components/ProjectFormModal'
import { EmptyState, Spinner } from '../components/ui'
import { api, errorMessage } from '../lib/api'
import { formatDateTime } from '../lib/format'
import { ROLE_LABELS, type Project } from '../lib/types'

function ProjectCard({ project }: { project: Project }) {
  return (
    <Link to={`/projects/${project.id}`} className={`project-card ${project.archived ? 'is-archived' : ''}`}>
      <div className="project-card__top">
        <span className="project-card__mark" aria-hidden="true">
          {project.name.slice(0, 1).toUpperCase()}
        </span>
        <span className={`badge badge--role-${project.role.toLowerCase()}`}>{ROLE_LABELS[project.role]}</span>
      </div>
      <h3>{project.name}</h3>
      <p className="project-card__description">
        {project.description || 'Sin descripción todavía.'}
      </p>
      <div className="project-card__meta">
        <span>{project.memberCount} {project.memberCount === 1 ? 'miembro' : 'miembros'}</span>
        <span>Actualizado {formatDateTime(project.updatedAt)}</span>
      </div>
      {project.archived && <span className="project-card__archived">Archivado</span>}
    </Link>
  )
}

export function ProjectsPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [showArchived, setShowArchived] = useState(false)
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.listProjects('all'),
  })

  const allProjects = projectsQuery.data?.content ?? []
  const projects = showArchived ? allProjects : allProjects.filter((project) => !project.archived)
  const visible = projects.filter((project) => {
    const term = query.trim().toLowerCase()
    if (!term) return true
    return (
      project.name.toLowerCase().includes(term) ||
      (project.description ?? '').toLowerCase().includes(term)
    )
  })
  const active = allProjects.filter((project) => !project.archived).length
  const owned = allProjects.filter((project) => project.role === 'OWNER' && !project.archived).length

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">Espacio de trabajo</p>
          <h1>Hola, {user?.fullName.split(' ')[0]}</h1>
          <p className="muted">
            {active > 0
              ? `Tienes ${active} ${active === 1 ? 'proyecto activo' : 'proyectos activos'}.`
              : 'Crea tu primer proyecto para empezar a organizar el trabajo.'}
          </p>
        </div>
        <button type="button" className="button button--primary" onClick={() => setCreating(true)}>
          <span aria-hidden="true">+</span> Nuevo proyecto
        </button>
      </header>

      <section className="stat-row" aria-label="Resumen">
        <article className="stat-card">
          <span className="stat-card__label">Proyectos activos</span>
          <strong>{active}</strong>
          <span className="stat-card__hint">Disponibles para el equipo</span>
        </article>
        <article className="stat-card">
          <span className="stat-card__label">Bajo tu propiedad</span>
          <strong>{owned}</strong>
          <span className="stat-card__hint">Puedes editar y gestiónar miembros</span>
        </article>
        <article className="stat-card">
          <span className="stat-card__label">Archivados</span>
          <strong>{allProjects.length - active}</strong>
          <span className="stat-card__hint">{showArchived ? 'Visibles en la lista' : 'Ocultos por defecto'}</span>
        </article>
      </section>

      <section className="panel">
        <div className="panel__toolbar">
          <div>
            <h2>Proyectos</h2>
            <p className="muted">{projects.length} en total</p>
          </div>
          <div className="toolbar-actions">
            <label className="search-field">
              <span aria-hidden="true">⌕</span>
              <input
                type="search"
                placeholder="Buscar proyecto..."
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </label>
            <label className="switch">
              <input
                type="checkbox"
                checked={showArchived}
                onChange={(event) => setShowArchived(event.target.checked)}
              />
              <span className="switch__track" aria-hidden="true" />
              Incluir archivados
            </label>
          </div>
        </div>

        {projectsQuery.isPending && (
          <div className="inline-loading">
            <Spinner /> Cargando proyectos...
          </div>
        )}

        {projectsQuery.isError && (
          <div className="alert alert--error">{errorMessage(projectsQuery.error)}</div>
        )}

        {projectsQuery.isSuccess && visible.length === 0 && (
          <EmptyState
            title={query ? 'Sin resultados' : 'Aún no hay proyectos'}
            description={
              query
                ? 'Prueba con otro termino de busqueda.'
                : 'Crea un proyecto y empieza a repartir tareas con tu equipo.'
            }
            action={
              !query ? (
                <button type="button" className="button button--primary" onClick={() => setCreating(true)}>
                  Crear proyecto
                </button>
              ) : undefined
            }
          />
        )}

        {visible.length > 0 && (
          <div className="project-grid">
            {visible.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        )}
      </section>

      {creating && (
        <ProjectFormModal
          onClose={() => setCreating(false)}
          onSaved={(project) => navigate(`/projects/${project.id}`)}
        />
      )}
    </div>
  )
}
