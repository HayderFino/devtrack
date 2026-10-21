import { useEffect, type ReactNode } from 'react'

export function Spinner({ size = 'md' }: { size?: 'sm' | 'md' }) {
  return <span className={`spinner spinner--${size}`} aria-hidden="true" />
}

export function FullPageSpinner({ label = 'Cargando...' }: { label?: string }) {
  return (
    <div className="full-page-spinner">
      <Spinner />
      <p>{label}</p>
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description: string
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <div className="empty-state__mark" aria-hidden="true">
        +
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  )
}

export function Modal({
  title,
  description,
  onClose,
  children,
  width = 'md',
}: {
  title: string
  description?: string
  onClose: () => void
  children: ReactNode
  width?: 'sm' | 'md' | 'lg'
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    document.body.classList.add('modal-open')
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.classList.remove('modal-open')
    }
  }, [onClose])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className={`modal modal--${width}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="modal__header">
          <div>
            <h2>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar">
            ×
          </button>
        </header>
        {children}
      </section>
    </div>
  )
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirmar',
  danger = false,
  busy = false,
  onConfirm,
  onClose,
}: {
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Modal title={title} onClose={onClose} width="sm">
      <div className="modal__body">
        <p className="confirm-message">{message}</p>
      </div>
      <footer className="modal__footer">
        <button type="button" className="button button--ghost" onClick={onClose} disabled={busy}>
          Cancelar
        </button>
        <button
          type="button"
          className={`button ${danger ? 'button--danger' : 'button--primary'}`}
          onClick={onConfirm}
          disabled={busy}
        >
          {busy && <Spinner size="sm" />}
          {confirmLabel}
        </button>
      </footer>
    </Modal>
  )
}
