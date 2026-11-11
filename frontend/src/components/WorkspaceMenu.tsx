import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Invitation, Workspace } from '../api/types'
import { useResource } from '../api/useResource'
import { ROLE_LABELS } from '../pages/roles'
import { useInvitationsLive } from '../realtime/useInvitationsLive'
import { DeleteZone } from './DeleteZone'
import { Alert, Field } from './Form'
import { InviteForm, PendingInvitations } from './Invitations'

type Action = 'edit' | 'invite' | 'delete'

/** The three-dot menu on a workspace in the list: rename, invite people, delete (only the creator/owner). */
export function WorkspaceMenu({ workspace, onChanged }: { workspace: Workspace; onChanged: () => void }) {
  const [open, setOpen] = useState(false)
  const [action, setAction] = useState<Action | null>(null)
  const box = useRef<HTMLDivElement>(null)
  const can = (permission: string) => workspace.permissions.includes(permission as never)
  const items: { action: Action; label: string; danger?: boolean }[] = [
    ...(can('UPDATE_SETTINGS') ? [{ action: 'edit' as const, label: 'Sửa tên' }] : []),
    ...(can('MANAGE_MEMBERS') ? [{ action: 'invite' as const, label: 'Mời thành viên' }] : []),
    ...(can('DELETE_WORKSPACE') ? [{ action: 'delete' as const, label: 'Xóa workspace', danger: true }] : []),
  ]

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  if (items.length === 0) return null
  return (
    <div className={`ws-menu ${open ? 'open' : ''}`} ref={box}>
      <button type="button" className="button-secondary button-small ws-menu-button" aria-haspopup="menu" aria-expanded={open} aria-label={`Tùy chọn cho ${workspace.name}`} onClick={() => setOpen((v) => !v)}>
        ⋯
      </button>
      {open && (
        <ul className="ws-menu-list" role="menu">
          {items.map((item) => (
            <li key={item.action} role="none">
              <button
                type="button"
                role="menuitem"
                className={item.danger ? 'ws-menu-item danger' : 'ws-menu-item'}
                onClick={() => {
                  setOpen(false)
                  setAction(item.action)
                }}
              >
                {item.label}
              </button>
            </li>
          ))}
        </ul>
      )}
      {action && (
        <Modal title={workspace.name} onClose={() => setAction(null)}>
          {action === 'edit' && <RenameForm workspace={workspace} onSaved={() => (setAction(null), onChanged())} />}
          {action === 'invite' && <InvitePanel workspace={workspace} />}
          {action === 'delete' && (
            <DeleteZone
              title="Xóa workspace"
              name={workspace.name}
              warning="Xóa vĩnh viễn workspace cùng toàn bộ team, dự án, công việc, tài liệu và trò chuyện bên trong. Không thể khôi phục."
              path={`/api/workspaces/${workspace.id}`}
              onDeleted={() => (setAction(null), onChanged())}
            />
          )}
        </Modal>
      )}
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal card stack" role="dialog" aria-modal="true" aria-label={title}>
        <div className="section-header">
          <h2>{title}</h2>
          <button type="button" className="button-secondary button-small" onClick={onClose} aria-label="Đóng">
            ✕
          </button>
        </div>
        <div className="modal-body stack">{children}</div>
      </div>
    </div>
  )
}

function RenameForm({ workspace, onSaved }: { workspace: Workspace; onSaved: () => void }) {
  const [name, setName] = useState(workspace.name)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // Only the name changes; the description and logo are sent back as they are.
      await api<Workspace>(`/api/workspaces/${workspace.id}`, { method: 'PUT', body: { name, description: workspace.description, logoUrl: workspace.logoUrl } })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={save} className="stack">
      <Field label="Tên workspace" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      <Alert>{error}</Alert>
      <button type="submit" disabled={busy || !name.trim()}>
        Lưu
      </button>
    </form>
  )
}

function InvitePanel({ workspace }: { workspace: Workspace }) {
  const { data, reload } = useResource<Invitation[]>(`/api/workspaces/${workspace.id}/invitations`)
  useInvitationsLive(reload)
  const roles = [
    ...(workspace.permissions.includes('MANAGE_ADMINS') ? [{ value: 'ADMIN', label: ROLE_LABELS.ADMIN }] : []),
    { value: 'MEMBER', label: ROLE_LABELS.MEMBER },
  ]
  return (
    <div className="stack">
      <p className="muted small">Mời người cùng làm việc trong workspace này (xem được mọi team trong workspace chỉ khi được thêm vào team đó).</p>
      <InviteForm path={`/api/workspaces/${workspace.id}/invitations`} roles={roles} onCreated={reload} />
      <h3>Lời mời đang chờ</h3>
      <PendingInvitations invitations={data ?? []} onChanged={reload} />
    </div>
  )
}
