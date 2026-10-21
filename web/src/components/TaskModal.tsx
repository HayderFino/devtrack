import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../lib/api'
import {
  PRIORITY_LABELS,
  STATUS_LABELS,
  TASK_PRIORITIES,
  TASK_STATUSES,
  type ProjectMember,
  type Task,
  type TaskPriority,
  type TaskStatus,
} from '../lib/types'
import { useToast } from './Toast'
import { Modal, Spinner } from './ui'

export const ALLOWED_TRANSITIONS: Record<TaskStatus, TaskStatus[]> = {
  TODO: ['IN_PROGRESS', 'DONE', 'CANCELLED'],
  IN_PROGRESS: ['TODO', 'DONE', 'CANCELLED'],
  DONE: ['IN_PROGRESS', 'CANCELLED'],
  CANCELLED: ['TODO'],
}

export function availableStatuses(task?: Task): TaskStatus[] {
  if (!task) return TASK_STATUSES
  return [task.status, ...ALLOWED_TRANSITIONS[task.status]]
}

export function TaskModal({
  projectId,
  task,
  members,
  onClose,
}: {
  projectId: number
  task?: Task
  members: ProjectMember[]
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const editing = Boolean(task)
  const [title, setTitle] = useState(task?.title ?? '')
  const [description, setDescription] = useState(task?.description ?? '')
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM')
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'TODO')
  const [assignee, setAssignee] = useState<string>(task?.assigneeId ? String(task.assigneeId) : '')
  const [dueDate, setDueDate] = useState(task?.dueDate ?? '')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: async () => {
      const payload = {
        title: title.trim(),
        description: description.trim(),
        priority,
        status,
        ...(dueDate ? { dueDate } : {}),
      }

      if (!task) {
        return api.createTask(projectId, {
          ...payload,
          ...(assignee ? { assigneeId: Number(assignee) } : {}),
        })
      }

      let updated = await api.updateTask(projectId, task.id, payload)
      const nextAssignee = assignee ? Number(assignee) : null
      if ((task.assigneeId ?? null) !== nextAssignee) {
        updated = await api.assignTask(projectId, task.id, nextAssignee)
      }
      return updated
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] })
      notify(editing ? 'Tarea actualizada' : 'Tarea creada')
      onClose()
    },
    onError: (cause) => setError(errorMessage(cause)),
  })

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    mutation.mutate()
  }

  return (
    <Modal
      title={editing ? 'Editar tarea' : 'Nueva tarea'}
      description={
        editing ? 'Cambia el contenido, el estado o el responsable.' : 'Define que hay que hacer y quien se encarga.'
      }
      onClose={onClose}
      width="lg"
    >
      <form onSubmit={onSubmit}>
        <div className="modal__body form-stack">
          {error && <div className="alert alert--error">{error}</div>}

          <label className="field">
            <span>Titulo</span>
            <input
              autoFocus
              required
              maxLength={200}
              placeholder="Ej. Preparar el release de noviembre"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>

          <label className="field">
            <span>Descripcion <small>opcional</small></span>
            <textarea
              rows={4}
              maxLength={10000}
              placeholder="Contexto, criterios de aceptacion o enlaces."
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>

          <div className="form-grid">
            <label className="field">
              <span>Estado</span>
              <select value={status} onChange={(event) => setStatus(event.target.value as TaskStatus)}>
                {availableStatuses(task).map((value) => (
                  <option key={value} value={value}>
                    {STATUS_LABELS[value]}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Prioridad</span>
              <select
                value={priority}
                onChange={(event) => setPriority(event.target.value as TaskPriority)}
              >
                {TASK_PRIORITIES.map((value) => (
                  <option key={value} value={value}>
                    {PRIORITY_LABELS[value]}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Responsable</span>
              <select value={assignee} onChange={(event) => setAssignee(event.target.value)}>
                <option value="">Sin asignar</option>
                {members.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.fullName}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Fecha limite <small>opcional</small></span>
              <input type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} />
              {editing && (
                <small>Dejar vacio no elimina una fecha ya guardada.</small>
              )}
            </label>
          </div>

          {editing && (
            <p className="form-note">
              Transiciones disponibles desde {STATUS_LABELS[task!.status]}:{' '}
              {ALLOWED_TRANSITIONS[task!.status].map((value) => STATUS_LABELS[value]).join(', ')}.
            </p>
          )}
        </div>

        <footer className="modal__footer">
          <button type="button" className="button button--ghost" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </button>
          <button type="submit" className="button button--primary" disabled={mutation.isPending || !title.trim()}>
            {mutation.isPending && <Spinner size="sm" />}
            {editing ? 'Guardar cambios' : 'Crear tarea'}
          </button>
        </footer>
      </form>
    </Modal>
  )
}
