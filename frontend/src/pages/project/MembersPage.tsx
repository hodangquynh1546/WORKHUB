import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ProjectMember, ProjectRole, WorkspaceMember } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useAuth } from '../../auth/AuthContext'
import { Alert } from '../../components/Form'
import { PROJECT_ROLE_LABELS, PROJECT_ROLES } from '../roles'
import { useProject } from './projectContext'
import { useLiveReload } from '../../realtime/useLiveReload'

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
      <p className="muted small">Product Owner và Scrum Master chỉ có một người; dự án luôn cần ít nhất một Project Manager.</p>
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
                    <RoleOptions />
                  </select>
                ) : (
                  PROJECT_ROLE_LABELS[m.role]
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
    return <p className="muted small add-hint">Mọi thành viên workspace đã ở trong dự án. Người ngoài workspace: hãy mời vào team hoặc workspace trước.</p>
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
