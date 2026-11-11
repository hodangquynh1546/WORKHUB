import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Navigate } from 'react-router'
import { api } from '../../api/client'
import { resizeToSquare } from '../../api/resizeImage'
import { Avatar } from '../../components/Avatar'
import { errorMessage } from '../../api/errors'
import type { Methodology, Project, ProjectStatus, WorkflowEntry } from '../../api/types'
import { useWorkflow } from '../../api/useWorkflow'
import { Alert, Field } from '../../components/Form'
import { METHODOLOGY_LABELS, PROJECT_STATUS_LABELS, PROJECT_STATUSES } from '../roles'
import { AlertSettingsCard } from './AlertSettingsCard'
import { useProject } from './projectContext'

type Message = { ok: boolean; text: string } | null

export function SettingsPage() {
  const { project } = useProject()
  // Opened by URL without the permission: the backend would refuse anyway.
  if (!project.permissions.includes('UPDATE_PROJECT')) return <Navigate to=".." replace />
  return (
    <div className="two-column">
      <DetailsForm />
      <div className="stack-lg">
        <StatusForm />
        <WorkflowForm />
        <AlertSettingsCard projectId={project.id} />
      </div>
    </div>
  )
}

/** Chooses a picture from the computer; it is cropped to a small square and saved at once. */
function ProjectPicture() {
  const { project, reload } = useProject()
  const input = useRef<HTMLInputElement>(null)
  const [message, setMessage] = useState<Message>(null)
  const [busy, setBusy] = useState(false)
  const archived = project.status === 'ARCHIVED'

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true)
    setMessage(null)
    try {
      await action()
      reload()
      setMessage({ ok: true, text: done })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  async function choose(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setMessage({ ok: false, text: 'Hãy chọn một tệp ảnh.' })
      return
    }
    await run(async () => {
      const form = new FormData()
      form.append('file', await resizeToSquare(file), 'project.jpg')
      await api<Project>(`/api/projects/${project.id}/avatar`, { method: 'PUT', body: form })
    }, 'Đã cập nhật ảnh dự án.')
  }

  return (
    <div className="stack">
      <span className="muted small">Ảnh đại diện</span>
      <div className="account-identity">
        <button type="button" className="avatar-edit" aria-label="Chọn ảnh dự án từ máy" disabled={busy || archived} onClick={() => input.current?.click()}>
          <Avatar name={project.name} url={project.avatarUrl} size="lg" />
          <span className="avatar-edit-hint">Đổi ảnh</span>
        </button>
        {project.avatarUrl && (
          <button type="button" className="button-secondary button-small" disabled={busy || archived} onClick={() => run(() => api<Project>(`/api/projects/${project.id}/avatar`, { method: 'DELETE' }), 'Đã xóa ảnh dự án.')}>
            Xóa ảnh
          </button>
        )}
        <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={choose} />
      </div>
      {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
    </div>
  )
}

function DetailsForm() {
  const { project, reload } = useProject()
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description ?? '')
  const [startDate, setStartDate] = useState(project.startDate ?? '')
  const [endDate, setEndDate] = useState(project.endDate ?? '')
  const [methodology, setMethodology] = useState<Methodology>(project.methodology)
  const [message, setMessage] = useState<Message>(null)
  const archived = project.status === 'ARCHIVED'

  async function save(event: FormEvent) {
    event.preventDefault()
    setMessage(null)
    try {
      await api<Project>(`/api/projects/${project.id}`, {
        method: 'PUT',
        body: { name, description, startDate: startDate || null, endDate: endDate || null, methodology },
      })
      reload()
      setMessage({ ok: true, text: 'Đã lưu thay đổi.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    }
  }

  return (
    <section className="card">
      <h2>Thông tin dự án</h2>
      <form onSubmit={save} className="stack">
        <Field label="Mã dự án (không đổi được)" value={project.key} disabled />
        <Field label="Tên dự án" required maxLength={100} disabled={archived} value={name} onChange={(e) => setName(e.target.value)} />
        <Field label="Mô tả" maxLength={2000} disabled={archived} value={description} onChange={(e) => setDescription(e.target.value)} />
        <ProjectPicture />
        <div className="inline-form">
          <Field label="Bắt đầu" type="date" disabled={archived} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          <Field label="Kết thúc dự kiến" type="date" min={startDate || undefined} disabled={archived} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        </div>
        <label className="field">
          <span>Phương pháp</span>
          <select value={methodology} disabled={archived} onChange={(e) => setMethodology(e.target.value as Methodology)}>
            {Object.entries(METHODOLOGY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit" disabled={archived}>
          Lưu
        </button>
      </form>
    </section>
  )
}

function StatusForm() {
  const { project, reload } = useProject()
  const [message, setMessage] = useState<Message>(null)

  async function change(status: ProjectStatus) {
    if (status === project.status) return
    if (status === 'ARCHIVED' && !window.confirm('Lưu trữ dự án? Dự án sẽ chuyển sang chế độ chỉ xem cho đến khi đổi trạng thái.')) return
    setMessage(null)
    try {
      await api<Project>(`/api/projects/${project.id}/status`, { method: 'PUT', body: { status } })
      reload()
      setMessage({ ok: true, text: `Đã chuyển sang "${PROJECT_STATUS_LABELS[status]}".` })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    }
  }

  return (
    <section className="card stack">
      <h2>Trạng thái</h2>
      <label className="field">
        <span>Trạng thái dự án</span>
        <select value={project.status} onChange={(e) => change(e.target.value as ProjectStatus)}>
          {PROJECT_STATUSES.map((status) => (
            <option key={status} value={status}>
              {PROJECT_STATUS_LABELS[status]}
            </option>
          ))}
        </select>
      </label>
      {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
    </section>
  )
}

const REQUIRED = new Set(['TO_DO', 'IN_PROGRESS', 'DONE'])

/** Rename the board columns and hide the optional ones (Code review, Testing). */
function WorkflowForm() {
  const { project } = useProject()
  const workflow = useWorkflow(project.id)
  if (!workflow.entries) return null
  return <WorkflowEditor key={JSON.stringify(workflow.entries)} projectId={project.id} entries={workflow.entries} onSaved={workflow.reload} />
}

function WorkflowEditor({ projectId, entries, onSaved }: { projectId: string; entries: WorkflowEntry[]; onSaved: () => void }) {
  const [rows, setRows] = useState(entries)
  const [message, setMessage] = useState<Message>(null)

  const change = (status: string, patch: Partial<WorkflowEntry>) => setRows((list) => list.map((r) => (r.status === status ? { ...r, ...patch } : r)))

  async function save(event: FormEvent) {
    event.preventDefault()
    setMessage(null)
    try {
      await api(`/api/projects/${projectId}/workflow`, { method: 'PUT', body: rows })
      onSaved()
      setMessage({ ok: true, text: 'Đã lưu cấu hình cột.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    }
  }

  return (
    <section className="card stack">
      <h2>Cột của Board</h2>
      <p className="muted small">Đổi tên các cột và ẩn Code review / Kiểm thử nếu team không dùng. Chỉ ẩn được khi không còn công việc ở trạng thái đó.</p>
      <form onSubmit={save} className="stack">
        {rows.map((row) => (
          <div key={row.status} className="inline-form">
            <Field label={row.status} required maxLength={40} value={row.name} onChange={(e) => change(row.status, { name: e.target.value })} />
            <label className="inline-check">
              <input type="checkbox" checked={row.enabled} disabled={REQUIRED.has(row.status)} onChange={(e) => change(row.status, { enabled: e.target.checked })} /> Dùng
            </label>
          </div>
        ))}
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit">Lưu cấu hình cột</button>
      </form>
    </section>
  )
}
