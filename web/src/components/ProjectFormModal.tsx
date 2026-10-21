import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../lib/api'
import type { Project } from '../lib/types'
import { useToast } from './Toast'
import { Modal, Spinner } from './ui'

export function ProjectFormModal({
  project,
  onClose,
  onSaved,
}: {
  project?: Project
  onClose: () => void
  onSaved?: (project: Project) => void
}) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [error, setError] = useState<string | null>(null)
  const editing = Boolean(project)

  const mutation = useMutation({
    mutationFn: () =>
      project
        ? api.updateProject(project.id, { name: name.trim(), description: description.trim() })
        : api.createProject({ name: name.trim(), description: description.trim() || undefined }),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      queryClient.invalidateQueries({ queryKey: ['project', saved.id] })
      notify(editing ? 'Proyecto actualizado' : 'Proyecto creado')
      onSaved?.(saved)
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
      title={editing ? 'Editar proyecto' : 'Nuevo proyecto'}
      description={
        editing ? 'Actualiza el nombre o la descripcion del proyecto.' : 'Crea un espacio para organizar el trabajo.'
      }
      onClose={onClose}
    >
      <form onSubmit={onSubmit}>
        <div className="modal__body form-stack">
          {error && <div className="alert alert--error">{error}</div>}

          <label className="field">
            <span>Nombre</span>
            <input
              autoFocus
              required
              maxLength={120}
              placeholder="Ej. Rediseno del portal"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <label className="field">
            <span>Descripcion <small>opcional</small></span>
            <textarea
              rows={4}
              maxLength={5000}
              placeholder="Objetivo, alcance o cualquier contexto util."
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
        </div>
        <footer className="modal__footer">
          <button type="button" className="button button--ghost" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </button>
          <button type="submit" className="button button--primary" disabled={mutation.isPending || !name.trim()}>
            {mutation.isPending && <Spinner size="sm" />}
            {editing ? 'Guardar cambios' : 'Crear proyecto'}
          </button>
        </footer>
      </form>
    </Modal>
  )
}
