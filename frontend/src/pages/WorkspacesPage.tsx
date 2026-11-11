import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Workspace } from '../api/types'
import { useResource } from '../api/useResource'
import { Alert, Field } from '../components/Form'
import { useInvitationsLive } from '../realtime/useInvitationsLive'
import { WorkspaceMenu } from '../components/WorkspaceMenu'
import { MyInvitations } from '../components/MyInvitations'
import { ROLE_LABELS } from './roles'
import { ListSkeleton } from '../components/Skeleton'

export function WorkspacesPage() {
  const navigate = useNavigate()
  const { data: workspaces, error: loadError, reload } = useResource<Workspace[]>('/api/workspaces')
  useInvitationsLive(reload)
  const invited = workspaces?.filter((ws) => ws.myRole !== 'OWNER') ?? []
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function create(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setCreateError(null)
    try {
      const created = await api<Workspace>('/api/workspaces', { method: 'POST', body: { name, description } })
      navigate(`/workspaces/${created.id}`)
    } catch (err) {
      setCreateError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <>
      <MyInvitations />
      <div className="two-column">
        <section>
          <h2>Workspace của bạn</h2>
          <Alert>{loadError}</Alert>
          {workspaces === null && !loadError && <ListSkeleton />}
          {workspaces?.length === 0 && <p className="muted">Bạn chưa tham gia workspace nào. Hãy tạo workspace đầu tiên.</p>}
          <WorkspaceCards workspaces={workspaces?.filter((ws) => ws.myRole === 'OWNER') ?? []} onChanged={reload} />
          {invited.length > 0 && (
            <>
              <h2 className="list-subtitle">Workspace được mời</h2>
              <WorkspaceCards workspaces={invited} onChanged={reload} />
            </>
          )}
        </section>
        <section className="card">
          <h2>Tạo workspace mới</h2>
          <form onSubmit={create} className="stack">
            <Field label="Tên workspace" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
            <Field label="Mô tả (không bắt buộc)" maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
            <Alert>{createError}</Alert>
            <button type="submit" disabled={busy}>
              Tạo workspace
            </button>
          </form>
        </section>
      </div>
    </>
  )
}

function WorkspaceCards({ workspaces, onChanged }: { workspaces: Workspace[]; onChanged: () => void }) {
  return (
    <ul className="card-list">
      {workspaces.map((ws) => (
        <li key={ws.id} className="workspace-item">
          <Link to={`/workspaces/${ws.id}`} className="card workspace-card">
            <WorkspaceLogo workspace={ws} />
            <div>
              <strong>{ws.name}</strong>
              {ws.description && <p className="muted">{ws.description}</p>}
            </div>
            <span className="badge">{ROLE_LABELS[ws.myRole]}</span>
          </Link>
          <WorkspaceMenu workspace={ws} onChanged={onChanged} />
        </li>
      ))}
    </ul>
  )
}

export function WorkspaceLogo({ workspace }: { workspace: Pick<Workspace, 'name' | 'logoUrl'> }) {
  return workspace.logoUrl ? (
    <img className="logo" src={workspace.logoUrl} alt="" />
  ) : (
    <span className="logo logo-fallback" aria-hidden="true">
      {workspace.name.trim().charAt(0).toUpperCase()}
    </span>
  )
}
