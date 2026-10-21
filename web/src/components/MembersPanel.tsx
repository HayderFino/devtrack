import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../lib/api'
import { formatDate, initials } from '../lib/format'
import { ROLE_LABELS, type Project, type ProjectMember, type ProjectRole } from '../lib/types'
import { ConfirmDialog, Modal, Spinner } from './ui'
import { useToast } from './Toast'

type AssignableRole = Exclude<ProjectRole, 'OWNER'>

function AddMemberModal({ projectId, onClose }: { projectId: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<AssignableRole>('EDITOR')
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => api.addMember(projectId, Number(userId), role),
    onSuccess: (member) => {
      queryClient.invalidateQueries({ queryKey: ['members', projectId] })
      queryClient.invalidateQueries({ queryKey: ['project', projectId] })
      queryClient.invalidateQueries({ queryKey: ['projects'] })
      notify(`${member.fullName} se ha unido al proyecto`)
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
      title="Añadir miembro"
      description="El usuario debe tener cuenta en DevTrack. Comparte su ID de perfil para incorporarlo."
      onClose={onClose}
    >
      <form onSubmit={onSubmit}>
        <div className="modal__body form-stack">
          {error && <div className="alert alert--error">{error}</div>}

          <label className="field">
            <span>ID del usuario</span>
            <input
              autoFocus
              type="number"
              min={1}
              required
              placeholder="Ej. 42"
              value={userId}
              onChange={(event) => setUserId(event.target.value)}
            />
            <small>El usuario encuentra su ID en la tarjeta de sesión, abajo a la izquierda.</small>
          </label>

          <label className="field">
            <span>Rol en el proyecto</span>
            <select value={role} onChange={(event) => setRole(event.target.value as AssignableRole)}>
              <option value="EDITOR">Editor · puede crear y modificar tareas</option>
              <option value="VIEWER">Lector · solo consulta</option>
            </select>
          </label>
        </div>

        <footer className="modal__footer">
          <button type="button" className="button button--ghost" onClick={onClose} disabled={mutation.isPending}>
            Cancelar
          </button>
          <button type="submit" className="button button--primary" disabled={mutation.isPending || !userId}>
            {mutation.isPending && <Spinner size="sm" />}
            Añadir miembro
          </button>
        </footer>
      </form>
    </Modal>
  )
}

export function MembersPanel({ project, members }: { project: Project; members: ProjectMember[] }) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<ProjectMember | null>(null)
  const [transferring, setTransferring] = useState<ProjectMember | null>(null)

  const isOwner = project.role === 'OWNER' && !project.archived

  function refresh() {
    queryClient.invalidateQueries({ queryKey: ['members', project.id] })
    queryClient.invalidateQueries({ queryKey: ['project', project.id] })
    queryClient.invalidateQueries({ queryKey: ['projects'] })
  }

  const roleMutation = useMutation({
    mutationFn: ({ member, role }: { member: ProjectMember; role: AssignableRole }) =>
      api.changeMemberRole(project.id, member.userId, role),
    onSuccess: (member) => {
      refresh()
      notify(`${member.fullName} ahora es ${ROLE_LABELS[member.role].toLowerCase()}`)
    },
    onError: (cause) => notify(errorMessage(cause), 'error'),
  })

  const removeMutation = useMutation({
    mutationFn: (member: ProjectMember) => api.removeMember(project.id, member.userId),
    onSuccess: () => {
      refresh()
      notify('Miembro eliminado del proyecto')
      setRemoving(null)
    },
    onError: (cause) => notify(errorMessage(cause), 'error'),
  })

  const transferMutation = useMutation({
    mutationFn: (member: ProjectMember) => api.transferOwnership(project.id, member.userId),
    onSuccess: () => {
      refresh()
      notify('La propiedad del proyecto ha sido transferida')
      setTransferring(null)
    },
    onError: (cause) => notify(errorMessage(cause), 'error'),
  })

  return (
    <section className="panel members-panel">
      <div className="panel__toolbar">
        <div>
          <h2>Equipo</h2>
          <p className="muted">{members.length} {members.length === 1 ? 'persona' : 'personas'}</p>
        </div>
        {isOwner && (
          <button type="button" className="button button--soft button--sm" onClick={() => setAdding(true)}>
            + Añadir
          </button>
        )}
      </div>

      <ul className="member-list">
        {members.map((member) => {
          const isCurrentOwner = member.role === 'OWNER'
          const busy =
            (roleMutation.isPending && roleMutation.variables?.member.userId === member.userId) ||
            (removeMutation.isPending && removeMutation.variables?.userId === member.userId) ||
            (transferMutation.isPending && transferMutation.variables?.userId === member.userId)

          return (
            <li key={member.userId} className="member">
              <span className="avatar">{initials(member.fullName)}</span>
              <div className="member__info">
                <strong>{member.fullName}</strong>
                <small>
                  {member.email} · #{member.userId}
                </small>
                <small className="member__since">Desde {formatDate(member.addedAt)}</small>
              </div>

              {isOwner && !isCurrentOwner ? (
                <div className="member__actions">
                  <select
                    className="select-compact"
                    aria-label={`Rol de ${member.fullName}`}
                    value={member.role}
                    disabled={busy}
                    onChange={(event) =>
                      roleMutation.mutate({ member, role: event.target.value as AssignableRole })
                    }
                  >
                    <option value="EDITOR">Editor</option>
                    <option value="VIEWER">Lector</option>
                  </select>
                  <button
                    type="button"
                    className="text-button"
                    onClick={() => setTransferring(member)}
                    disabled={busy}
                  >
                    Hacer owner
                  </button>
                  <button
                    type="button"
                    className="text-button text-button--danger"
                    onClick={() => setRemoving(member)}
                    disabled={busy}
                  >
                    Quitar
                  </button>
                  {busy && <Spinner size="sm" />}
                </div>
              ) : (
                <span className={`badge badge--role-${member.role.toLowerCase()}`}>
                  {ROLE_LABELS[member.role]}
                </span>
              )}
            </li>
          )
        })}
      </ul>

      {project.archived && (
        <p className="panel__note">El proyecto está archivado: la gestión de miembros está deshabilitada.</p>
      )}

      {adding && <AddMemberModal projectId={project.id} onClose={() => setAdding(false)} />}

      {removing && (
        <ConfirmDialog
          title="Quitar miembro"
          message={`${removing.fullName} perderá acceso al proyecto y a todas sus tareas.`}
          confirmLabel="Quitar acceso"
          danger
          busy={removeMutation.isPending}
          onConfirm={() => removeMutation.mutate(removing)}
          onClose={() => setRemoving(null)}
        />
      )}

      {transferring && (
        <ConfirmDialog
          title="Transferir propiedad"
          message={`${transferring.fullName} pasará a ser propietario y tu quedarás como editor. Esta acción cambia los permisos de todo el equipo.`}
          confirmLabel="Transferir"
          busy={transferMutation.isPending}
          onConfirm={() => transferMutation.mutate(transferring)}
          onClose={() => setTransferring(null)}
        />
      )}
    </section>
  )
}
