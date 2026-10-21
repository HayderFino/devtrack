import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { api, errorMessage } from '../lib/api'
import { formatDate, initials } from '../lib/format'
import {
  PRIORITY_LABELS,
  STATUS_LABELS,
  TASK_PRIORITIES,
  TASK_STATUSES,
  type Project,
  type ProjectMember,
  type Task,
  type TaskFilters,
  type TaskPriority,
  type TaskStatus,
} from '../lib/types'
import { useDebouncedValue } from '../lib/useDebounce'
import { ConfirmDialog, EmptyState, Spinner } from './ui'
import { TaskModal, availableStatuses } from './TaskModal'
import { useToast } from './Toast'

function TaskCard({
  task,
  canEdit,
  canDelete,
  busy,
  onEdit,
  onDelete,
  onStatusChange,
}: {
  task: Task
  canEdit: boolean
  canDelete: boolean
  busy: boolean
  onEdit: () => void
  onDelete: () => void
  onStatusChange: (status: TaskStatus) => void
}) {
  return (
    <article className={`task-card task-card--${task.priority.toLowerCase()} ${task.overdue ? 'is-overdue' : ''}`}>
      <div className="task-card__top">
        <span className={`priority priority--${task.priority.toLowerCase()}`}>
          {PRIORITY_LABELS[task.priority]}
        </span>
        {task.overdue && <span className="badge badge--danger">Vencida</span>}
      </div>

      <h4>{task.title}</h4>
      {task.description && <p className="task-card__description">{task.description}</p>}

      <div className="task-card__meta">
        <span className="task-assignee">
          {task.assigneeName ? (
            <>
              <span className="avatar avatar--sm">{initials(task.assigneeName)}</span>
              {task.assigneeName}
            </>
          ) : (
            <span className="muted">Sin responsable</span>
          )}
        </span>
        {task.dueDate && (
          <span className={`due-date ${task.overdue ? 'is-overdue' : ''}`}>
            {formatDate(task.dueDate)}
          </span>
        )}
      </div>

      {canEdit && (
        <div className="task-card__actions">
          <select
            aria-label="Cambiar estado"
            className="select-compact"
            value={task.status}
            disabled={busy}
            onChange={(event) => onStatusChange(event.target.value as TaskStatus)}
          >
            {availableStatuses(task).map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
          </select>
          <button type="button" className="text-button" onClick={onEdit} disabled={busy}>
            Editar
          </button>
          {canDelete && (
            <button type="button" className="text-button text-button--danger" onClick={onDelete} disabled={busy}>
              Eliminar
            </button>
          )}
          {busy && <Spinner size="sm" />}
        </div>
      )}
    </article>
  )
}

export function TaskBoard({ project, members }: { project: Project; members: ProjectMember[] }) {
  const { user } = useAuth()
  const { notify } = useToast()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search)
  const [status, setStatus] = useState<TaskStatus | ''>('')
  const [priority, setPriority] = useState<TaskPriority | ''>('')
  const [assignee, setAssignee] = useState('')
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Task | null>(null)
  const [deleting, setDeleting] = useState<Task | null>(null)

  const readOnly = project.archived
  const canEdit = !readOnly && (project.role === 'OWNER' || project.role === 'EDITOR')

  const filters: TaskFilters = {
    q: debouncedSearch || undefined,
    status: status ? [status] : undefined,
    priority: priority ? [priority] : undefined,
    assigneeId: assignee && assignee !== 'unassigned' ? Number(assignee) : undefined,
    unassigned: assignee === 'unassigned',
    overdue: overdueOnly || undefined,
  }

  const tasksQuery = useQuery({
    queryKey: ['tasks', project.id, filters],
    queryFn: () => api.listTasks(project.id, filters),
  })

  const statusMutation = useMutation({
    mutationFn: ({ taskId, next }: { taskId: number; next: TaskStatus }) =>
      api.updateTask(project.id, taskId, { status: next }),
    onSuccess: (task) => {
      queryClient.invalidateQueries({ queryKey: ['tasks', project.id] })
      notify(`Tarea movida a ${STATUS_LABELS[task.status]}`)
    },
    onError: (cause) => notify(errorMessage(cause), 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: (task: Task) => api.deleteTask(project.id, task.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', project.id] })
      notify('Tarea eliminada')
      setDeleting(null)
    },
    onError: (cause) => notify(errorMessage(cause), 'error'),
  })

  const tasks = tasksQuery.data?.content ?? []
  const hasFilters = Boolean(debouncedSearch || status || priority || assignee || overdueOnly)

  function clearFilters() {
    setSearch('')
    setStatus('')
    setPriority('')
    setAssignee('')
    setOverdueOnly(false)
  }

  function canDelete(task: Task): boolean {
    if (!canEdit) return false
    return project.role === 'OWNER' || task.createdById === user?.id
  }

  return (
    <section className="panel task-panel">
      <div className="panel__toolbar panel__toolbar--stack">
        <div className="task-toolbar__heading">
          <div>
            <h2>Tareas</h2>
            <p className="muted">
              {tasksQuery.data ? `${tasksQuery.data.totalElements} en total` : 'Cargando tablero...'}
            </p>
          </div>
          {canEdit && (
            <button type="button" className="button button--primary button--sm" onClick={() => setCreating(true)}>
              <span aria-hidden="true">+</span> Nueva tarea
            </button>
          )}
        </div>

        <div className="filters">
          <label className="search-field search-field--grow">
            <span aria-hidden="true">⌕</span>
            <input
              type="search"
              placeholder="Buscar por título o descripción..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>

          <select value={status} onChange={(event) => setStatus(event.target.value as TaskStatus | '')} aria-label="Filtrar por estado">
            <option value="">Todos los estados</option>
            {TASK_STATUSES.map((value) => (
              <option key={value} value={value}>
                {STATUS_LABELS[value]}
              </option>
            ))}
          </select>

          <select
            value={priority}
            onChange={(event) => setPriority(event.target.value as TaskPriority | '')}
            aria-label="Filtrar por prioridad"
          >
            <option value="">Todas las prioridades</option>
            {TASK_PRIORITIES.map((value) => (
              <option key={value} value={value}>
                {PRIORITY_LABELS[value]}
              </option>
            ))}
          </select>

          <select value={assignee} onChange={(event) => setAssignee(event.target.value)} aria-label="Filtrar por responsable">
            <option value="">Cualquier responsable</option>
            <option value="unassigned">Sin asignar</option>
            {members.map((member) => (
              <option key={member.userId} value={member.userId}>
                {member.fullName}
              </option>
            ))}
          </select>

          <label className="check">
            <input type="checkbox" checked={overdueOnly} onChange={(event) => setOverdueOnly(event.target.checked)} />
            Solo vencidas
          </label>

          {hasFilters && (
            <button type="button" className="text-button" onClick={clearFilters}>
              Limpiar
            </button>
          )}
        </div>
      </div>

      {tasksQuery.isPending && (
        <div className="inline-loading">
          <Spinner /> Cargando tareas...
        </div>
      )}

      {tasksQuery.isError && <div className="alert alert--error">{errorMessage(tasksQuery.error)}</div>}

      {tasksQuery.isSuccess && tasks.length === 0 && (
        <EmptyState
          title={hasFilters ? 'Ninguna tarea coincide' : 'El tablero está vacío'}
          description={
            hasFilters
              ? 'Prueba a quitar algún filtro para ver más resultados.'
              : canEdit
                ? 'Crea la primera tarea y asignala a alguien del equipo.'
                : 'Cuando el equipo cree tareas aparecerán aquí.'
          }
          action={
            hasFilters ? (
              <button type="button" className="button button--ghost" onClick={clearFilters}>
                Quitar filtros
              </button>
            ) : canEdit ? (
              <button type="button" className="button button--primary" onClick={() => setCreating(true)}>
                Crear tarea
              </button>
            ) : undefined
          }
        />
      )}

      {tasks.length > 0 && (
        <div className="board">
          {TASK_STATUSES.map((column) => {
            const columnTasks = tasks.filter((task) => task.status === column)
            return (
              <div key={column} className={`board-column board-column--${column.toLowerCase()}`}>
                <header className="board-column__header">
                  <span className={`status-dot status-dot--${column.toLowerCase()}`} />
                  <h3>{STATUS_LABELS[column]}</h3>
                  <span className="count">{columnTasks.length}</span>
                </header>

                <div className="board-column__body">
                  {columnTasks.map((task) => (
                    <TaskCard
                      key={task.id}
                      task={task}
                      canEdit={canEdit}
                      canDelete={canDelete(task)}
                      busy={
                        (statusMutation.isPending && statusMutation.variables?.taskId === task.id) ||
                        (deleteMutation.isPending && deleteMutation.variables?.id === task.id)
                      }
                      onEdit={() => setEditing(task)}
                      onDelete={() => setDeleting(task)}
                      onStatusChange={(next) => {
                        if (next === task.status) return
                        statusMutation.mutate({ taskId: task.id, next })
                      }}
                    />
                  ))}
                  {columnTasks.length === 0 && <p className="board-column__empty">Sin tareas</p>}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {creating && <TaskModal projectId={project.id} members={members} onClose={() => setCreating(false)} />}
      {editing && (
        <TaskModal projectId={project.id} task={editing} members={members} onClose={() => setEditing(null)} />
      )}
      {deleting && (
        <ConfirmDialog
          title="Eliminar tarea"
          message={`Se eliminará "${deleting.title}" de forma permanente. Esta acción no se puede deshacer.`}
          confirmLabel="Eliminar"
          danger
          busy={deleteMutation.isPending}
          onConfirm={() => deleteMutation.mutate(deleting)}
          onClose={() => setDeleting(null)}
        />
      )}
    </section>
  )
}
