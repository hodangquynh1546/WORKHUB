import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ProjectMember, ProjectRole, WorkspaceMember } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useAuth } from '../../auth/AuthContext'
import { Alert } from '../../components/Form'
import type { Invitation } from '../../api/types'
import { useInvitationsLive } from '../../realtime/useInvitationsLive'
import { InviteForm, PendingInvitations } from '../../components/Invitations'
import { PROJECT_ROLE_LABELS, PROJECT_ROLES } from '../roles'
import { useProject } from './projectContext'
import { useLiveReload } from '../../realtime/useLiveReload'

/** Checkboxes for the member's roles; every change is saved at once, and one role must always stay ticked. */
function RolePicker({ member, holders, onChange }: { member: ProjectMember; holders: ProjectMember[]; onChange: (roles: ProjectRole[]) => void }) {
  function toggle(role: ProjectRole, on: boolean) {
    const next = on ? [...member.roles, role] : member.roles.filter((r) => r !== role)
    if (next.length === 0) return
    const holder = on ? holders.find((h) => h.userId !== member.userId && h.roles.includes(role)) : undefined
    if (holder && !window.confirm(`${PROJECT_ROLE_LABELS[role]} hiện là ${holder.fullName}. Chuyển vai trò này sang ${member.fullName}?`)) return
    onChange(next)
  }
  return (
    <details className="role-picker">
      <summary>{member.roles.map((r) => PROJECT_ROLE_LABELS[r]).join(', ')}</summary>
      <div className="role-picker-list">
        {PROJECT_ROLES.map((role) => (
          <label key={role} className="inline-check">
            <input type="checkbox" checked={member.roles.includes(role)} onChange={(e) => toggle(role, e.target.checked)} /> {PROJECT_ROLE_LABELS[role]}
          </label>
        ))}
      </div>
    </details>
  )
}

function RoleOptions() {
  return PROJECT_ROLES.map((role) => (
    <option key={role} value={role}>
      {PROJECT_ROLE_LABELS[role]}
    </option>
  ))
}

export function MembersPage() {
  const { project, reload: reloadProject } = useProject()
  const { user } = useAuth()
  const navigate = useNavigate()
  const { data: members, error, reload } = useResource<ProjectMember[]>(`/api/projects/${project.id}/members`)
  useLiveReload(project.id, ['members'], reload)
  useInvitationsLive(reload)
  const [actionError, setActionError] = useState<string | null>(null)
  const canManage = project.permissions.includes('MANAGE_MEMBERS') && project.status !== 'ARCHIVED'
  const memberPath = (userId: string) => `/api/projects/${project.id}/members/${userId}`

  async function run(action: () => Promise<unknown>) {
    setActionError(null)
    try {
      await action()
      reload()
      reloadProject()
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  async function leave() {
    if (!user || !window.confirm(`Rời dự án ${project.name}?`)) return
    setActionError(null)
    try {
      await api(memberPath(user.id), { method: 'DELETE' })
      navigate(`/teams/${project.teamId}`)
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  return (
    <section className="card">
      <h2>Thành viên dự án ({members?.length ?? '…'})</h2>
      <p className="muted small">Một thành viên có thể giữ nhiều vai trò (quyền cộng dồn). Project Manager và Scrum Master chỉ có một người: gán cho người khác thì người đang giữ được chuyển sang vai trò còn lại (hoặc Developer).</p>
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
                  <RolePicker
                    member={m}
                    holders={members}
                    onChange={(roles) => run(() => api(memberPath(m.userId), { method: 'PUT', body: { roles } }))}
                  />
                ) : (
                  m.roles.map((r) => PROJECT_ROLE_LABELS[r]).join(', ')
                )}
              </td>
              <td className="cell-actions">
                {canManage && m.userId !== user?.id && (
                  <button
                    type="button"
                    className="button-secondary button-small"
                    onClick={() => window.confirm(`Xóa ${m.fullName} khỏi dự án?`) && run(() => api(memberPath(m.userId), { method: 'DELETE' }))}
                  >
                    Xóa
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {canManage && members && <AddMember projectId={project.id} workspaceId={project.workspaceId} existing={members} onAdded={() => run(async () => undefined)} />}
      {canManage && <ProjectInvitations projectId={project.id} />}
      {project.myRole && (
        <button type="button" className="button-danger leave-button" onClick={leave}>
          Rời dự án
        </button>
      )}
    </section>
  )
}

/** Adds an existing workspace member; people outside the workspace are invited to the workspace or team first. */
function AddMember({ projectId, workspaceId, existing, onAdded }: { projectId: string; workspaceId: string; existing: ProjectMember[]; onAdded: () => void }) {
  const { data: workspaceMembers } = useResource<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`)
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<ProjectRole>('DEVELOPER')
  const [error, setError] = useState<string | null>(null)
  const candidates = (workspaceMembers ?? []).filter((wm) => !existing.some((m) => m.userId === wm.userId))

  async function add(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/projects/${projectId}/members`, { method: 'POST', body: { userId, role } })
      setUserId('')
      onAdded()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (candidates.length === 0) {
    return <p className="muted small add-hint">Mọi thành viên workspace đã ở trong dự án. Người chưa ở trong workspace: dùng mục "Mời người mới vào dự án" bên dưới.</p>
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
      <select value={role} onChange={(e) => setRole(e.target.value as ProjectRole)} aria-label="Vai trò">
        <RoleOptions />
      </select>
      <button type="submit">Thêm</button>
      <Alert>{error}</Alert>
    </form>
  )
}

function ProjectInvitations({ projectId }: { projectId: string }) {
  const { data, reload } = useResource<Invitation[]>(`/api/projects/${projectId}/invitations`)
  useInvitationsLive(reload)
  const roles = PROJECT_ROLES.map((value) => ({ value, label: PROJECT_ROLE_LABELS[value] }))
  return (
    <section className="card stack">
      <h2>Mời người mới vào dự án</h2>
      <p className="muted">Mời bằng email hoặc liên kết, kể cả người chưa có trong workspace. Họ sẽ vào workspace với vai trò thành viên và vào dự án với vai trò bạn chọn.</p>
      <InviteForm path={`/api/projects/${projectId}/invitations`} roles={roles} onCreated={reload} />
      <h3>Lời mời đang chờ</h3>
      <PendingInvitations invitations={data ?? []} onChanged={reload} />
    </section>
  )
}
