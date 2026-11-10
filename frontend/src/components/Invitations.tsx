import { useState, type FormEvent } from 'react'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Invitation, InvitationCreated, InvitationKind } from '../api/types'
import { formatDate, invitationRoleLabel } from '../pages/roles'
import { Alert, Field } from './Form'

type RoleOption = { value: string; label: string }

/** Creates an email or link invitation at `path` (workspace or team invitations endpoint). */
export function InviteForm({ path, roles, onCreated }: { path: string; roles: RoleOption[]; onCreated: () => void }) {
  const [kind, setKind] = useState<InvitationKind>('EMAIL')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState(roles[roles.length - 1].value)
  const [created, setCreated] = useState<InvitationCreated | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setCreated(null)
    setCopied(false)
    try {
      const body = kind === 'EMAIL' ? { kind, email: email.trim(), role } : { kind, role }
      setCreated(await api<InvitationCreated>(path, { method: 'POST', body }))
      setEmail('')
      onCreated()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="stack">
      <div className="segmented" role="radiogroup" aria-label="Cách mời">
        <label>
          <input type="radio" checked={kind === 'EMAIL'} onChange={() => setKind('EMAIL')} /> Qua email
        </label>
        <label>
          <input type="radio" checked={kind === 'LINK'} onChange={() => setKind('LINK')} /> Liên kết mời
        </label>
      </div>
      {kind === 'EMAIL' ? (
        <Field label="Email người được mời" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      ) : (
        <p className="muted">Ai có liên kết đều tham gia được, cho tới khi liên kết hết hạn (7 ngày) hoặc bị thu hồi.</p>
      )}
      <label className="field">
        <span>Vai trò</span>
        <select value={role} onChange={(e) => setRole(e.target.value)}>
          {roles.map((r) => (
            <option key={r.value} value={r.value}>
              {r.label}
            </option>
          ))}
        </select>
      </label>
      <Alert>{error}</Alert>
      {created && (
        <div className="alert alert-success stack">
          <span>
            {created.invitation.kind === 'EMAIL'
              ? `Đã gửi lời mời tới ${created.invitation.email}. (Môi trường dev: email nằm trong log backend.)`
              : 'Đã tạo liên kết mời. Hãy sao chép ngay: liên kết chỉ hiển thị một lần.'}
          </span>
          <code className="invite-url">{created.inviteUrl}</code>
          <button
            type="button"
            className="button-secondary"
            onClick={async () => {
              await navigator.clipboard.writeText(created.inviteUrl)
              setCopied(true)
            }}
          >
            {copied ? 'Đã sao chép' : 'Sao chép liên kết'}
          </button>
        </div>
      )}
      <button type="submit" disabled={busy}>
        {kind === 'EMAIL' ? 'Gửi lời mời' : 'Tạo liên kết'}
      </button>
    </form>
  )
}

/** Pending invitations with a revoke button. */
export function PendingInvitations({ invitations, onChanged }: { invitations: Invitation[]; onChanged: () => void }) {
  const [error, setError] = useState<string | null>(null)

  async function revoke(invitation: Invitation) {
    if (!window.confirm('Thu hồi lời mời này?')) return
    try {
      await api(`/api/invitations/${invitation.id}`, { method: 'DELETE' })
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (invitations.length === 0) return <p className="muted">Không có lời mời nào đang chờ.</p>
  return (
    <>
      <Alert>{error}</Alert>
      <ul className="plain-list">
        {invitations.map((inv) => (
          <li key={inv.id} className="row">
            <div>
              <strong>{inv.kind === 'EMAIL' ? inv.email : 'Liên kết mời'}</strong>
              <p className="muted small">
                {invitationRoleLabel(inv.teamId, inv.role, inv.projectId)}
                {inv.teamName && ` · team ${inv.teamName}`}{inv.projectName && ` · dự án ${inv.projectName}`} · mời bởi {inv.invitedByName} · hết hạn {formatDate(inv.expiresAt)}
              </p>
            </div>
            <button type="button" className="button-secondary" onClick={() => revoke(inv)}>
              Thu hồi
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}
