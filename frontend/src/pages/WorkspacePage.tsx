import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Workspace, WorkspaceMember } from '../api/types'
import { Alert, Field } from '../components/Form'
import { ROLE_LABELS } from './roles'
import { WorkspaceLogo } from './WorkspacesPage'

export function WorkspacePage() {
  const { workspaceId } = useParams()
  const [workspace, setWorkspace] = useState<Workspace | null>(null)
  const [members, setMembers] = useState<WorkspaceMember[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      api<Workspace>(`/api/workspaces/${workspaceId}`),
      api<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`),
    ])
      .then(([ws, list]) => {
        setWorkspace(ws)
        setMembers(list)
      })
      .catch((err) => setLoadError(errorMessage(err)))
  }, [workspaceId])

  if (loadError) {
    return (
      <>
        <Alert>{loadError}</Alert>
        <Link to="/workspaces">← Danh sách workspace</Link>
      </>
    )
  }
  if (!workspace) return <p className="muted">Đang tải…</p>

  const canManage = workspace.myRole === 'OWNER' || workspace.myRole === 'ADMIN'

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
      </header>
      <div className="two-column">
        <section className="card">
          <h2>Thành viên ({members.length})</h2>
          <p className="muted">Tính năng mời thành viên sẽ có ở giai đoạn tiếp theo (M2).</p>
          <table className="table">
            <thead>
              <tr>
                <th>Họ tên</th>
                <th>Email</th>
                <th>Vai trò</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.userId}>
                  <td>{m.fullName}</td>
                  <td>{m.email}</td>
                  <td>{ROLE_LABELS[m.role]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        {canManage && <WorkspaceSettings workspace={workspace} onSaved={setWorkspace} />}
      </div>
    </>
  )
}

function WorkspaceSettings({ workspace, onSaved }: { workspace: Workspace; onSaved: (ws: Workspace) => void }) {
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
      const saved = await api<Workspace>(`/api/workspaces/${workspace.id}`, {
        method: 'PUT',
        body: { name, description, logoUrl: logoUrl.trim() || null },
      })
      onSaved(saved)
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
