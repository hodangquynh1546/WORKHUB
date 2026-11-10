import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Invitation, Team, Workspace, WorkspaceMember, WorkspaceRole } from '../api/types'
import { useResource } from '../api/useResource'
import { useAuth } from '../auth/AuthContext'
import { Alert, Field } from '../components/Form'
import { InviteForm, PendingInvitations } from '../components/Invitations'
import { ROLE_LABELS } from './roles'
import { WorkspaceLogo } from './WorkspacesPage'

export function WorkspacePage() {
  const { workspaceId } = useParams()
  const { data: workspace, error, reload } = useResource<Workspace>(`/api/workspaces/${workspaceId}`)

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to="/workspaces">← Danh sách workspace</Link>
      </>
    )
  }
  if (!workspace) return <p className="muted">Đang tải…</p>

  const can = (p: Workspace['permissions'][number]) => workspace.permissions.includes(p)

  return (
    <>
      <Link to="/workspaces" className="muted-link">
        ← Danh sách workspace
      </Link>
      <header className="workspace-header">
        <WorkspaceLogo workspace={workspace} />
        <div>
          <h1>{workspace.name}</h1>
          {workspace.description && <p className="muted">{workspace.description}</p>}
        </div>
        <span className="badge">{ROLE_LABELS[workspace.myRole]}</span>
      </header>
      <div className="two-column">
        <div className="stack-lg">
          <TeamsSection workspace={workspace} />
          <MembersSection workspace={workspace} />
        </div>
        <div className="stack-lg">
          {can('MANAGE_MEMBERS') && <InvitationsSection workspace={workspace} />}
          {can('UPDATE_SETTINGS') && <WorkspaceSettings workspace={workspace} onSaved={reload} />}
          {workspace.myRole !== 'OWNER' && <LeaveWorkspace workspace={workspace} />}
        </div>
      </div>
    </>
  )
}

function TeamsSection({ workspace }: { workspace: Workspace }) {
  const navigate = useNavigate()
  const [showArchived, setShowArchived] = useState(false)
  const { data: teams, error } = useResource<Team[]>(`/api/workspaces/${workspace.id}/teams?archived=${showArchived}`)
  const [name, setName] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)

  async function create(event: FormEvent) {
    event.preventDefault()
    setCreateError(null)
    try {
      const team = await api<Team>(`/api/workspaces/${workspace.id}/teams`, { method: 'POST', body: { name } })
      navigate(`/teams/${team.id}`)
    } catch (err) {
      setCreateError(errorMessage(err))
    }
  }

  return (
    <section className="card">
      <div className="section-header">
        <h2>Teams</h2>
        <label className="inline-check">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Đã lưu trữ
        </label>
      </div>
      <Alert>{error}</Alert>
      {teams?.length === 0 && <p className="muted">{showArchived ? 'Không có team nào đã lưu trữ.' : 'Chưa có team nào.'}</p>}
      <ul className="plain-list">
        {teams?.map((team) => (
          <li key={team.id}>
            <Link to={`/teams/${team.id}`} className="row row-link">
              <div>
                <strong>{team.name}</strong>
                <p className="muted small">
                  {team.memberCount} thành viên{team.myRole === 'LEAD' && ' · bạn là trưởng nhóm'}
                  {team.myRole === 'MEMBER' && ' · bạn là thành viên'}
                </p>
              </div>
              <span aria-hidden="true">›</span>
            </Link>
          </li>
        ))}
      </ul>
      {workspace.permissions.includes('MANAGE_TEAMS') && !showArchived && (
        <form onSubmit={create} className="inline-form">
          <Field label="Tạo team mới" placeholder="Ví dụ: Backend Team" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          <button type="submit">Tạo team</button>
        </form>
      )}
      <Alert>{createError}</Alert>
    </section>
  )
}

function MembersSection({ workspace }: { workspace: Workspace }) {
  const { user } = useAuth()
  const { data: members, error, reload } = useResource<WorkspaceMember[]>(`/api/workspaces/${workspace.id}/members`)
  const [actionError, setActionError] = useState<string | null>(null)
  const can = (p: Workspace['permissions'][number]) => workspace.permissions.includes(p)

  async function run(action: () => Promise<unknown>) {
    setActionError(null)
    try {
      await action()
      reload()
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  const changeRole = (m: WorkspaceMember, role: WorkspaceRole) =>
    run(() => api(`/api/workspaces/${workspace.id}/members/${m.userId}`, { method: 'PUT', body: { role } }))
  const remove = (m: WorkspaceMember) =>
    window.confirm(`Xóa ${m.fullName} khỏi workspace? Người này cũng sẽ rời mọi team trong workspace.`) &&
    run(() => api(`/api/workspaces/${workspace.id}/members/${m.userId}`, { method: 'DELETE' }))

  return (
    <section className="card">
      <h2>Thành viên ({members?.length ?? '…'})</h2>
      <Alert>{error ?? actionError}</Alert>
      <table className="table">
        <thead>
          <tr>
            <th>Họ tên</th>
            <th>Email</th>
            <th>Vai trò</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {members?.map((m) => {
            const isOwner = m.role === 'OWNER'
            const canRemove = !isOwner && m.userId !== user?.id && can(m.role === 'ADMIN' ? 'MANAGE_ADMINS' : 'MANAGE_MEMBERS')
            return (
              <tr key={m.userId}>
                <td>{m.fullName}</td>
                <td>{m.email}</td>
                <td>
                  {can('MANAGE_ADMINS') && !isOwner ? (
                    <select value={m.role} onChange={(e) => changeRole(m, e.target.value as WorkspaceRole)} aria-label="Vai trò">
                      <option value="ADMIN">{ROLE_LABELS.ADMIN}</option>
                      <option value="MEMBER">{ROLE_LABELS.MEMBER}</option>
                    </select>
                  ) : (
                    ROLE_LABELS[m.role]
                  )}
                </td>
                <td className="cell-actions">
                  {canRemove && (
                    <button type="button" className="button-secondary button-small" onClick={() => remove(m)}>
                      Xóa
                    </button>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}

function InvitationsSection({ workspace }: { workspace: Workspace }) {
  const { data, reload } = useResource<Invitation[]>(`/api/workspaces/${workspace.id}/invitations`)
  const roles = [
    ...(workspace.permissions.includes('MANAGE_ADMINS') ? [{ value: 'ADMIN', label: ROLE_LABELS.ADMIN }] : []),
    { value: 'MEMBER', label: ROLE_LABELS.MEMBER },
  ]
  return (
    <section className="card stack">
      <h2>Mời vào workspace</h2>
      <InviteForm path={`/api/workspaces/${workspace.id}/invitations`} roles={roles} onCreated={reload} />
      <h3>Lời mời đang chờ</h3>
      <PendingInvitations invitations={data ?? []} onChanged={reload} />
    </section>
  )
}

function WorkspaceSettings({ workspace, onSaved }: { workspace: Workspace; onSaved: () => void }) {
  const [name, setName] = useState(workspace.name)
  const [description, setDescription] = useState(workspace.description ?? '')
  const [logoUrl, setLogoUrl] = useState(workspace.logoUrl ?? '')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      await api<Workspace>(`/api/workspaces/${workspace.id}`, {
        method: 'PUT',
        body: { name, description, logoUrl: logoUrl.trim() || null },
      })
      onSaved()
      setMessage({ ok: true, text: 'Đã lưu thay đổi.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h2>Cài đặt workspace</h2>
      <form onSubmit={save} className="stack">
        <Field label="Tên workspace" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        <Field label="Mô tả" maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
        <Field
          label="Logo URL (http/https)"
          type="url"
          maxLength={1024}
          placeholder="https://…"
          value={logoUrl}
          onChange={(e) => setLogoUrl(e.target.value)}
        />
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit" disabled={busy}>
          Lưu
        </button>
      </form>
    </section>
  )
}

function LeaveWorkspace({ workspace }: { workspace: Workspace }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  async function leave() {
    if (!user || !window.confirm(`Rời workspace ${workspace.name}? Bạn sẽ rời mọi team trong workspace này.`)) return
    try {
      await api(`/api/workspaces/${workspace.id}/members/${user.id}`, { method: 'DELETE' })
      navigate('/workspaces')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card">
      <h2>Rời workspace</h2>
      <Alert>{error}</Alert>
      <button type="button" className="button-danger" onClick={leave}>
        Rời workspace
      </button>
    </section>
  )
}
