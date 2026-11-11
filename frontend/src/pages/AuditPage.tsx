import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import type { AuditEntry, Page } from '../api/types'
import { useResource } from '../api/useResource'
import { Alert } from '../components/Form'
import { ExportMenu } from '../components/ExportMenu'
import { ListSkeleton } from '../components/Skeleton'
import { AUDIT_FAMILIES, auditActionLabel, auditDetail } from './auditLabels'
import { useProject } from './project/projectContext'

type Scope = { kind: 'workspace'; id: string } | { kind: 'project'; id: string } | { kind: 'mine' }

const when = (iso: string) => new Date(iso).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'medium' })
/** Start of a local calendar day as an instant the backend understands. */
const startOfDay = (day: string) => new Date(`${day}T00:00:00`).toISOString()
const endOfDay = (day: string) => new Date(new Date(`${day}T00:00:00`).getTime() + 86_400_000).toISOString()

/** The audit log: who did what, to what, when. Read-only; owners/admins see a workspace, project managers a project. */
function AuditLog({ scope }: { scope: Scope }) {
  const [action, setAction] = useState('')
  const [q, setQ] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [failedOnly, setFailedOnly] = useState(false)
  const [size, setSize] = useState(50)

  const filters = useMemo(() => {
    const p = new URLSearchParams()
    if (action) p.set('action', action)
    if (q.trim()) p.set('q', q.trim())
    if (from) p.set('from', startOfDay(from))
    if (to) p.set('to', endOfDay(to))
    if (failedOnly) p.set('outcome', 'FAILED')
    return p.toString()
  }, [action, q, from, to, failedOnly])

  const base = scope.kind === 'workspace' ? `/api/workspaces/${scope.id}/audit-log` : scope.kind === 'project' ? `/api/projects/${scope.id}/audit-log` : '/api/me/audit-log'
  const { data, error } = useResource<Page<AuditEntry>>(`${base}?size=${size}${scope.kind === 'mine' ? '' : `&${filters}`}`)

  return (
    <div className="stack-lg">
      {scope.kind !== 'mine' && (
        <div className="filters">
          <select value={action} onChange={(e) => (setAction(e.target.value), setSize(50))} aria-label="Loại hành động">
            {AUDIT_FAMILIES.map((f) => (
              <option key={f.value} value={f.value}>
                {f.label}
              </option>
            ))}
          </select>
          <input type="search" placeholder="Tìm theo người, đối tượng hoặc chi tiết…" value={q} onChange={(e) => (setQ(e.target.value), setSize(50))} aria-label="Tìm trong nhật ký" />
          <label className="inline-check">
            Từ <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="Từ ngày" />
          </label>
          <label className="inline-check">
            đến <input type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="Đến ngày" />
          </label>
          <label className="inline-check">
            <input type="checkbox" checked={failedOnly} onChange={(e) => setFailedOnly(e.target.checked)} /> Chỉ thất bại
          </label>
          {scope.kind === 'workspace' && <ExportMenu options={[{ label: 'Nhật ký (theo bộ lọc)', path: `/api/workspaces/${scope.id}/audit-log/export?${filters}` }]} />}
        </div>
      )}
      <Alert>{error}</Alert>
      {!data && !error && <ListSkeleton rows={6} />}
      {data?.items.length === 0 && (
        <section className="card empty-state">
          <h2>Không có bản ghi nào</h2>
          <p className="muted">Nhật ký ghi lại các thay đổi về quyền, cấu trúc, xóa dữ liệu, xuất dữ liệu và đăng nhập. Thử bỏ bớt bộ lọc.</p>
        </section>
      )}
      {data && data.items.length > 0 && (
        <section className="card audit-card">
          <table className="table audit-table">
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>Người thực hiện</th>
                <th>Hành động</th>
                <th>Đối tượng</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((e) => (
                <tr key={e.id} className={e.outcome === 'FAILED' ? 'audit-failed' : ''}>
                  <td className="audit-when">{when(e.createdAt)}</td>
                  <td>
                    {e.actorName ?? '—'}
                    {e.ip && <p className="muted small">{e.ip}</p>}
                  </td>
                  <td>
                    <strong>{auditActionLabel(e.action)}</strong>
                    {e.outcome === 'FAILED' && <span className="badge alert-sev-critical audit-badge">Thất bại</span>}
                    {e.detail && <p className="muted small">{auditDetail(e.detail)}</p>}
                  </td>
                  <td>{e.targetLabel ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
      {data && data.total > data.items.length && (
        <button type="button" className="button-secondary" onClick={() => setSize((s) => s + 50)}>
          Xem thêm ({data.items.length}/{data.total})
        </button>
      )}
    </div>
  )
}

export function WorkspaceAuditPage() {
  const { workspaceId } = useParams()
  return (
    <>
      <Link to={`/workspaces/${workspaceId}`} className="muted-link">
        ← Về workspace
      </Link>
      <h1>Nhật ký hoạt động</h1>
      <p className="muted audit-intro">Bản ghi chỉ thêm, không sửa hay xóa được: ai đã đổi quyền, thêm hoặc xóa thành viên, xóa dữ liệu, xuất dữ liệu trong workspace này.</p>
      <AuditLog scope={{ kind: 'workspace', id: workspaceId ?? '' }} />
    </>
  )
}

export function ProjectAuditPage() {
  const { project } = useProject()
  return <AuditLog scope={{ kind: 'project', id: project.id }} />
}

export function MyActivityPage() {
  return (
    <>
      <h1>Hoạt động của tôi</h1>
      <p className="muted audit-intro">Các hành động quan trọng của chính bạn, kể cả đăng nhập thành công và thất bại. Thấy điều lạ thì hãy đổi mật khẩu ngay.</p>
      <AuditLog scope={{ kind: 'mine' }} />
    </>
  )
}
