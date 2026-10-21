import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { MembersPanel } from '../components/MembersPanel'
import { ProjectFormModal } from '../components/ProjectFormModal'
import { TaskBoard } from '../components/TaskBoard'
import { ConfirmDialog, FullPageSpinner } from '../components/ui'
import { useToast } from '../components/Toast'
import { api, errorMessage } from '../lib/api'
import { formatDate, initials } from '../lib/format'
import { ROLE_LABELS } from '../lib/types'

type Tab = 'tasks' | 'team'

export function ProjectDetailPage() {
  const params = useParams()
  const projectId = Number(params.projectId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [tab, setTab] = useState<Tab>('tasks')
  const [editing, setEditing] = useState(false)
  const [archiving, setArchiving] = useState(false)

  const projectQuery = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => api.getProject(projectId),
    enabled: Number.isFinite(projectId) && projectId > 0,
  })

  const membersQuery = useQuery({
    queryKey: ['members', projectId],
    queryFn: () => api.listMembers(projectId),
    enabled: Number.isFinite(projectId) && projectId > 0,
  })

  const archiveMutation = useMutation({
    mutationFn: () => api.archiveProject(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      queryClient.invalidateQueries({ queryKey: ['project', projectId] })
      notify('Proyecto archivado')
      navigate('/projects')
    },
    onError: (cause) => notify(errorMessage(cause), 'error'),
  })

  if (!Number.isFinite(projectId) || projectId <= 0) {
    return (
      <div className="page">
        <div className="alert alert--error">El identificador del proyecto no es valido.</div>
        <Link to="/projects" className="text-link">Volver a proyectos</Link>
      </div>
    )
  }

  if (projectQuery.isPending || membersQuery.isPending) {
    return <FullPageSpinner label="Cargando proyecto..." />
  }

  if (projectQuery.isError || !projectQuery.data) {
    return (
      <div className="page">
        <Link to="/projects" className="back-link">← Proyectos</Link>
        <div className="panel empty-state">
          <h3>No podemos abrir este proyecto</h3>
          <p>{errorMessage(projectQuery.error)}</p>
          <Link to="/projects" className="button button--primary">Volver a mis proyectos</Link>
        </div>
      </div>
    )
  }

  const project = projectQuery.data
  const members = membersQuery.data ?? []
  const isOwner = project.role === 'OWNER'
  const visibleMembers = members.slice(0, 4)

  return (
    <div className="page project-page">
      <Link to="/projects" className="back-link">← Proyectos</Link>

      <header className="project-header">
        <div className="project-header__main">
          <div className="project-header__title">
            <span className="project-header__mark" aria-hidden="true">
              {project.name.slice(0, 1).toUpperCase()}
            </span>
            <div>
              <div className="title-row">
                <h1>{project.name}</h1>
                <span className={`badge badge--role-${project.role.toLowerCase()}`}>
                  {ROLE_LABELS[project.role]}
                </span>
                {project.archived && <span className="badge badge--neutral">Archivado</span>}
              </div>
              <p className="muted">
                {project.description || 'Sin descripcion.'}
              </p>
            </div>
          </div>

          <div className="project-header__meta">
            <span>Creado el {formatDate(project.createdAt)}</span>
            <span>·</span>
            <span>{project.memberCount} {project.memberCount === 1 ? 'miembro' : 'miembros'}</span>
          </div>
        </div>

        <div className="project-header__actions">
          <div className="avatar-stack" aria-label="Miembros del equipo">
            {visibleMembers.map((member) => (
              <span key={member.userId} className="avatar avatar--sm" title={member.fullName}>
                {initials(member.fullName)}
              </span>
            ))}
            {members.length > visibleMembers.length && (
              <span className="avatar avatar--sm avatar--more">+{members.length - visibleMembers.length}</span>
            )}
          </div>
          {isOwner && !project.archived && (
            <>
              <button type="button" className="button button--ghost" onClick={() => setEditing(true)}>
                Editar
              </button>
              <button type="button" className="button button--ghost button--danger-text" onClick={() => setArchiving(true)}>
                Archivar
              </button>
            </>
          )}
        </div>
      </header>

      {project.archived && (
        <div className="alert alert--warning">
          Este proyecto esta archivado. Su contenido es de solo lectura; para trabajar de nuevo,
          pide a un administrador que lo reactive.
        </div>
      )}

      <nav className="tabs" aria-label="Secciones del proyecto">
        <button
          type="button"
          className={tab === 'tasks' ? 'tab is-active' : 'tab'}
          onClick={() => setTab('tasks')}
        >
          Tareas
        </button>
        <button
          type="button"
          className={tab === 'team' ? 'tab is-active' : 'tab'}
          onClick={() => setTab('team')}
        >
          Equipo <span className="count">{members.length}</span>
        </button>
      </nav>

      {tab === 'tasks' && <TaskBoard project={project} members={members} />}
      {tab === 'team' && <MembersPanel project={project} members={members} />}

      {editing && <ProjectFormModal project={project} onClose={() => setEditing(false)} />}
      {archiving && (
        <ConfirmDialog
          title="Archivar proyecto"
          message="El proyecto pasara a solo lectura y dejara de aparecer en la lista activa. Podras consultarlo desde el filtro de archivados."
          confirmLabel="Archivar proyecto"
          danger
          busy={archiveMutation.isPending}
          onConfirm={() => archiveMutation.mutate()}
          onClose={() => setArchiving(false)}
        />
      )}
    </div>
  )
}
