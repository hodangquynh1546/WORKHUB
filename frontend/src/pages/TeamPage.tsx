import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Invitation, Project, Team, TeamMember, TeamRole, WorkspaceMember } from '../api/types'
import { useResource } from '../api/useResource'
import { useAuth } from '../auth/AuthContext'
import { CreateProjectForm } from '../components/CreateProjectForm'
import { Alert, Field } from '../components/Form'
import { DeleteZone } from '../components/DeleteZone'
import { useInvitationsLive } from '../realtime/useInvitationsLive'
import { InviteForm, PendingInvitations } from '../components/Invitations'
import { ProjectList } from '../components/ProjectList'
import { TEAM_ROLE_LABELS } from './roles'
import { PageSkeleton } from '../components/Skeleton'
import { ExportMenu } from '../components/ExportMenu'

const TEAM_ROLE_OPTIONS = [
  { value: 'LEAD', label: TEAM_ROLE_LABELS.LEAD },
  { value: 'MEMBER', label: TEAM_ROLE_LABELS.MEMBER },
]

export function TeamPage() {
  const { teamId } = useParams()
  const navigate = useNavigate()
  const { data: team, error, reload } = useResource<Team>(`/api/teams/${teamId}`)
  useInvitationsLive(reload)

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to="/teams">← Danh sách team</Link>
      </>
    )
  }
  if (!team) return <PageSkeleton />

  const canManageMembers = team.permissions.includes('MANAGE_MEMBERS') && !team.archived

  return (
    <>
      <Link to={`/workspaces/${team.workspaceId}`} className="muted-link">
        ← Về workspace
      </Link>
      <header className="workspace-header">
        <div>
          <h1>
            {team.name} {team.archived && <span className="badge badge-muted">Đã lưu trữ</span>}
          </h1>
          {team.description && <p className="muted">{team.description}</p>}
        </div>
        {team.myRole && <span className="badge">{TEAM_ROLE_LABELS[team.myRole]}</span>}
        <div className="actions workspace-actions">
          <ExportMenu options={[{ label: 'Tổng hợp dự án của team', path: `/api/teams/${team.id}/export/projects` }]} />
        </div>
      </header>
      {team.archived && <Alert kind="info">Team đã được lưu trữ: chỉ xem, không thể thay đổi thành viên.</Alert>}
      <div className="two-column">
        <div className="stack-lg">
          <TeamProjects team={team} />
          <TeamMembers team={team} canManage={canManageMembers} onChanged={reload} />
        </div>
        <div className="stack-lg">
          {canManageMembers && <TeamInvitations team={team} />}
          {team.permissions.includes('UPDATE_TEAM') && <TeamSettings team={team} onSaved={reload} />}
          {team.permissions.includes('DELETE_TEAM') && (
            <DeleteZone
              title="Xóa team"
              name={team.name}
              warning="Xóa vĩnh viễn team cùng toàn bộ dự án của team (công việc, sprint, tài liệu, cuộc họp, trao đổi). Không thể khôi phục. Có thể dùng Lưu trữ nếu chỉ muốn ngừng làm việc."
              path={`/api/teams/${team.id}`}
              onDeleted={() => navigate(`/workspaces/${team.workspaceId}`)}
            />
          )}
        </div>
      </div>
    </>
  )
}

function TeamProjects({ team }: { team: Team }) {
  const { data: projects, error, reload } = useResource<Project[]>(`/api/teams/${team.id}/projects`)
  useInvitationsLive(reload)
  const [creating, setCreating] = useState(false)
  const canCreate = team.permissions.includes('MANAGE_PROJECTS') && !team.archived

  return (
    <section className="card">
      <div className="section-header">
        <h2>Dự án</h2>
        {canCreate && (
          <button type="button" className="button-secondary button-small" onClick={() => setCreating((v) => !v)}>
            {creating ? 'Đóng' : 'Tạo dự án'}
          </button>
        )}
      </div>
      <Alert>{error}</Alert>
      {creating && <CreateProjectForm teamId={team.id} />}
      {projects?.length === 0 && !creating && <p className="muted">Team chưa có dự án nào.</p>}
      {projects && <ProjectList projects={projects} />}
    </section>
  )
}

function TeamMembers({ team, canManage, onChanged }: { team: Team; canManage: boolean; onChanged: () => void }) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { data: members, error, reload } = useResource<TeamMember[]>(`/api/teams/${team.id}/members`)
  useInvitationsLive(reload)
  const [actionError, setActionError] = useState<string | null>(null)

  function refresh() {
    reload()
    onChanged()
  }

  async function run(action: () => Promise<unknown>) {
    setActionError(null)
    try {
      await action()
      refresh()
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  const memberPath = (userId: string) => `/api/teams/${team.id}/members/${userId}`

  async function leave() {
    if (!user || !window.confirm(`Rời team ${team.name}?`)) return
    try {
      await api(memberPath(user.id), { method: 'DELETE' })
      navigate(`/workspaces/${team.workspaceId}`)
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  return (
    <section className="card">
      <h2>Thành viên team ({members?.length ?? '…'})</h2>
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
          {members?.map((m) => (
            <tr key={m.userId}>
              <td>{m.fullName}</td>
              <td>{m.email}</td>
              <td>
                {canManage ? (
                  <select
                    value={m.role}
                    aria-label="Vai trò"
                    onChange={(e) => run(() => api(memberPath(m.userId), { method: 'PUT', body: { role: e.target.value } }))}
                  >
                    {TEAM_ROLE_OPTIONS.map((r) => (
                      <option key={r.value} value={r.value}>
                        {r.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  TEAM_ROLE_LABELS[m.role]
                )}
              </td>
              <td className="cell-actions">
                {canManage && m.userId !== user?.id && (
                  <button
                    type="button"
                    className="button-secondary button-small"
                    onClick={() => window.confirm(`Xóa ${m.fullName} khỏi team?`) && run(() => api(memberPath(m.userId), { method: 'DELETE' }))}
                  >
                    Xóa
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {canManage && members && (
        <AddFromWorkspace team={team} existing={members} onAdded={refresh} />
      )}
      {team.myRole && (
        <button type="button" className="button-danger leave-button" onClick={leave}>
          Rời team
        </button>
      )}
    </section>
  )
}

/** Adds someone who is already in the workspace; outsiders are invited instead. */
function AddFromWorkspace({ team, existing, onAdded }: { team: Team; existing: TeamMember[]; onAdded: () => void }) {
  const { data: workspaceMembers } = useResource<WorkspaceMember[]>(`/api/workspaces/${team.workspaceId}/members`)
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<TeamRole>('MEMBER')
  const [error, setError] = useState<string | null>(null)

  const candidates = (workspaceMembers ?? []).filter((wm) => !existing.some((m) => m.userId === wm.userId))

  async function add(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/teams/${team.id}/members`, { method: 'POST', body: { userId, role } })
      setUserId('')
      onAdded()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (candidates.length === 0) {
    return <p className="muted small add-hint">Mọi thành viên workspace đều đã ở trong team. Người ngoài workspace: hãy gửi lời mời.</p>
  }
  return (
    <form onSubmit={add} className="inline-form">
      <label className="field">
        <span>Thêm thành viên từ workspace</span>
        <select required value={userId} onChange={(e) => setUserId(e.target.value)}>
          <option value="">Chọn người…</option>
          {candidates.map((c) => (
            <option key={c.userId} value={c.userId}>
              {c.fullName} ({c.email})
            </option>
          ))}
        </select>
      </label>
      <select value={role} onChange={(e) => setRole(e.target.value as TeamRole)} aria-label="Vai trò">
        {TEAM_ROLE_OPTIONS.map((r) => (
          <option key={r.value} value={r.value}>
            {r.label}
          </option>
        ))}
      </select>
      <button type="submit">Thêm</button>
      <Alert>{error}</Alert>
    </form>
  )
}

function TeamInvitations({ team }: { team: Team }) {
  const { data, reload } = useResource<Invitation[]>(`/api/teams/${team.id}/invitations`)
  useInvitationsLive(reload)
  return (
    <section className="card stack">
      <h2>Mời vào team</h2>
      <p className="muted small">Người chưa ở trong workspace sẽ được thêm vào workspace với vai trò Thành viên.</p>
      <InviteForm path={`/api/teams/${team.id}/invitations`} roles={TEAM_ROLE_OPTIONS} onCreated={reload} />
      <h3>Lời mời đang chờ</h3>
      <PendingInvitations invitations={data ?? []} onChanged={reload} />
    </section>
  )
}

function TeamSettings({ team, onSaved }: { team: Team; onSaved: () => void }) {
  const [name, setName] = useState(team.name)
  const [description, setDescription] = useState(team.description ?? '')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  async function run(action: () => Promise<unknown>, success: string) {
    setMessage(null)
    try {
      await action()
      onSaved()
      setMessage({ ok: true, text: success })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    }
  }

  return (
    <section className="card">
      <h2>Cài đặt team</h2>
      {!team.archived && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            run(() => api(`/api/teams/${team.id}`, { method: 'PUT', body: { name, description } }), 'Đã lưu thay đổi.')
          }}
          className="stack"
        >
          <Field label="Tên team" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
          <Field label="Mô tả" maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
          <button type="submit">Lưu</button>
        </form>
      )}
      {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
      <button
        type="button"
        className="button-secondary archive-button"
        onClick={() =>
          team.archived
            ? run(() => api(`/api/teams/${team.id}/unarchive`, { method: 'POST' }), 'Đã khôi phục team.')
            : window.confirm('Lưu trữ team này? Team sẽ chuyển sang chế độ chỉ xem.') &&
              run(() => api(`/api/teams/${team.id}/archive`, { method: 'POST' }), 'Đã lưu trữ team.')
        }
      >
        {team.archived ? 'Khôi phục team' : 'Lưu trữ team'}
      </button>
    </section>
  )
}
