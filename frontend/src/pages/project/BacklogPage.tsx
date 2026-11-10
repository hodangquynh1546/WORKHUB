import { useEffect, useMemo, useState, type DragEvent, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Issue, IssueStatus, IssueType, Page, ProjectMember, RefinementStatus } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useWorkflow } from '../../api/useWorkflow'
import { Alert } from '../../components/Form'
import { Assignee, PriorityBadge, TypeBadge } from '../../components/IssueBits'
import {
  ISSUE_PRIORITIES,
  ISSUE_TYPES,
  PRIORITY_LABELS,
  REFINEMENT_LABELS,
  REFINEMENTS,
  TYPE_LABELS,
} from '../issueLabels'
import { useProject } from './projectContext'

const PAGE_SIZE = 200
/** Sub-tasks need a parent, so they are created from their parent's page. */
const QUICK_TYPES = ISSUE_TYPES.filter((t) => t !== 'SUB_TASK')

type Filters = {
  q: string
  type: IssueType | ''
  status: IssueStatus | ''
  priority: string
  assignee: string // '' | 'UNASSIGNED' | user id
  epicId: string
  label: string
  refinement: RefinementStatus | ''
  showSubtasks: boolean
  unplanned: boolean
}

const NO_FILTERS: Filters = { q: '', type: '', status: '', priority: '', assignee: '', epicId: '', label: '', refinement: '', showSubtasks: false, unplanned: false }

function buildQuery(f: Filters): string {
  const p = new URLSearchParams({ size: String(PAGE_SIZE) })
  if (f.q.trim()) p.set('q', f.q.trim())
  if (f.type) p.set('type', f.type)
  else if (!f.showSubtasks) ISSUE_TYPES.filter((t) => t !== 'SUB_TASK').forEach((t) => p.append('type', t))
  if (f.status) p.set('status', f.status)
  if (f.priority) p.set('priority', f.priority)
  if (f.assignee === 'UNASSIGNED') p.set('unassigned', 'true')
  else if (f.assignee) p.set('assigneeId', f.assignee)
  if (f.epicId) p.set('epicId', f.epicId)
  if (f.label) p.set('label', f.label)
  if (f.refinement) p.set('refinement', f.refinement)
  if (f.unplanned) p.set('unplanned', 'true')
  return p.toString()
}

export function BacklogPage() {
  const { project } = useProject()
  const active = project.status !== 'ARCHIVED'
  const canEdit = project.permissions.includes('EDIT_ISSUES') && active
  const canOrder = project.permissions.includes('MANAGE_BACKLOG') && active

  const [filters, setFilters] = useState<Filters>(NO_FILTERS)
  const [debouncedQ, setDebouncedQ] = useState('')
  const [groupByEpic, setGroupByEpic] = useState(false)
  const [items, setItems] = useState<Issue[] | null>(null)
  const [total, setTotal] = useState(0)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  const workflow = useWorkflow(project.id)
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${project.id}/members`)
  const { data: labels } = useResource<string[]>(`/api/projects/${project.id}/issue-labels`)
  const { data: epics } = useResource<Page<Issue>>(`/api/projects/${project.id}/issues?type=EPIC&size=${PAGE_SIZE}`)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQ(filters.q), 300)
    return () => clearTimeout(timer)
  }, [filters.q])

  const query = useMemo(() => buildQuery({ ...filters, q: debouncedQ }), [filters, debouncedQ])
  const filtered = query !== buildQuery(NO_FILTERS)
  // Reordering is only meaningful on the full, unfiltered backlog.
  const canDrag = canOrder && !filtered && !groupByEpic

  useEffect(() => {
    let cancelled = false
    api<Page<Issue>>(`/api/projects/${project.id}/issues?${query}`)
      .then((page) => {
        if (cancelled) return
        setItems(page.items)
        setTotal(page.total)
        setLoadError(null)
      })
      .catch((err) => !cancelled && setLoadError(errorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [project.id, query, version])

  const reload = () => setVersion((v) => v + 1)
  const set = <K extends keyof Filters>(key: K, value: Filters[K]) => setFilters((f) => ({ ...f, [key]: value }))

  function replace(updated: Issue) {
    setItems((list) => list?.map((i) => (i.id === updated.id ? { ...updated, childCount: i.childCount, childDoneCount: i.childDoneCount } : i)) ?? null)
  }

  async function mutate(call: () => Promise<Issue>) {
    setActionError(null)
    try {
      replace(await call())
    } catch (err) {
      setActionError(errorMessage(err))
      reload()
    }
  }

  const changeStatus = (issue: Issue, status: IssueStatus) =>
    mutate(() => api<Issue>(`/api/issues/${issue.id}/status`, { method: 'PUT', body: { status } }))
  const assign = (issue: Issue, assigneeId: string) =>
    mutate(() => api<Issue>(`/api/issues/${issue.id}/assignee`, { method: 'PUT', body: { assigneeId: assigneeId || null } }))

  /** Drops `moved` right before `target` (null = at the end). */
  async function move(movedId: string, targetId: string | null) {
    if (!items || movedId === targetId) return
    const others = items.filter((i) => i.id !== movedId)
    const at = targetId === null ? others.length : others.findIndex((i) => i.id === targetId)
    if (at < 0) return
    const moved = items.find((i) => i.id === movedId)!
    const afterIssueId = at > 0 ? others[at - 1].id : null
    setItems([...others.slice(0, at), moved, ...others.slice(at)])
    setActionError(null)
    try {
      await api(`/api/issues/${movedId}/rank`, { method: 'PUT', body: { afterIssueId } })
    } catch (err) {
      setActionError(errorMessage(err))
      reload()
    }
  }

  const groups = useMemo(() => {
    if (!items) return []
    if (!groupByEpic) return [{ key: 'all', title: null as string | null, issues: items }]
    const byEpic = new Map<string, { key: string; title: string | null; issues: Issue[] }>()
    for (const issue of items) {
      const epic = issue.type === 'EPIC' ? issue : issue.epic
      const key = epic?.id ?? 'none'
      if (!byEpic.has(key)) byEpic.set(key, { key, title: epic ? `${epic.key} · ${epic.title}` : 'Không thuộc Epic nào', issues: [] })
      byEpic.get(key)!.issues.push(issue)
    }
    return [...byEpic.values()].sort((a, b) => (a.key === 'none' ? 1 : b.key === 'none' ? -1 : 0))
  }, [items, groupByEpic])

  return (
    <section className="card stack">
      <div className="section-header">
        <h2>Product Backlog {items && <span className="muted small">({total} công việc)</span>}</h2>
        <label className="inline-check">
          <input type="checkbox" checked={groupByEpic} onChange={(e) => setGroupByEpic(e.target.checked)} /> Nhóm theo Epic
        </label>
      </div>
      {canEdit && <QuickCreate projectId={project.id} onCreated={reload} />}

      <div className="filters">
        <input type="search" placeholder="Tìm theo tiêu đề hoặc mã (ECOM-12)…" value={filters.q} onChange={(e) => set('q', e.target.value)} aria-label="Tìm kiếm" />
        <select value={filters.type} onChange={(e) => set('type', e.target.value as IssueType | '')} aria-label="Loại">
          <option value="">Mọi loại</option>
          {ISSUE_TYPES.map((t) => (
            <option key={t} value={t}>
              {TYPE_LABELS[t]}
            </option>
          ))}
        </select>
        <select value={filters.status} onChange={(e) => set('status', e.target.value as IssueStatus | '')} aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option>
          {workflow.enabled.map((s) => (
            <option key={s} value={s}>
              {workflow.names[s]}
            </option>
          ))}
        </select>
        <select value={filters.priority} onChange={(e) => set('priority', e.target.value)} aria-label="Độ ưu tiên">
          <option value="">Mọi độ ưu tiên</option>
          {ISSUE_PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {PRIORITY_LABELS[p]}
            </option>
          ))}
        </select>
        <select value={filters.assignee} onChange={(e) => set('assignee', e.target.value)} aria-label="Người phụ trách">
          <option value="">Mọi người</option>
          <option value="UNASSIGNED">Chưa giao</option>
          {members?.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.fullName}
            </option>
          ))}
        </select>
        <select value={filters.epicId} onChange={(e) => set('epicId', e.target.value)} aria-label="Epic">
          <option value="">Mọi Epic</option>
          {epics?.items.map((e) => (
            <option key={e.id} value={e.id}>
              {e.key} · {e.title}
            </option>
          ))}
        </select>
        <select value={filters.refinement} onChange={(e) => set('refinement', e.target.value as RefinementStatus | '')} aria-label="Làm rõ">
          <option value="">Mọi mức làm rõ</option>
          {REFINEMENTS.map((r) => (
            <option key={r} value={r}>
              {REFINEMENT_LABELS[r]}
            </option>
          ))}
        </select>
        <select value={filters.label} onChange={(e) => set('label', e.target.value)} aria-label="Nhãn">
          <option value="">Mọi nhãn</option>
          {labels?.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>
        <label className="inline-check">
          <input type="checkbox" checked={filters.unplanned} onChange={(e) => set('unplanned', e.target.checked)} /> Chưa vào Sprint
        </label>
        <label className="inline-check">
          <input type="checkbox" checked={filters.showSubtasks} onChange={(e) => set('showSubtasks', e.target.checked)} /> Hiện Sub-task
        </label>
        {filtered && (
          <button type="button" className="button-secondary button-small" onClick={() => setFilters(NO_FILTERS)}>
            Xóa bộ lọc
          </button>
        )}
      </div>

      <Alert>{loadError ?? actionError}</Alert>
      {items === null && !loadError && <p className="muted">Đang tải…</p>}
      {items?.length === 0 && <p className="muted">{filtered ? 'Không có công việc nào khớp bộ lọc.' : 'Backlog đang trống. Hãy thêm công việc đầu tiên ở trên.'}</p>}
      {items && total > items.length && <Alert kind="info">Đang hiển thị {items.length}/{total} công việc đầu tiên. Hãy dùng bộ lọc để thu hẹp danh sách.</Alert>}
      {canDrag && items && items.length > 1 && <p className="muted small">Kéo biểu tượng ⠿ để sắp xếp thứ tự ưu tiên.</p>}

      {groups.map((group) => (
        <div key={group.key}>
          {group.title && <h3 className="group-title">{group.title}</h3>}
          <ul className="plain-list issue-list">
            {group.issues.map((issue) => (
              <IssueRow
                key={issue.id}
                issue={issue}
                members={members ?? []}
                workflow={workflow}
                canEdit={canEdit}
                canDrag={canDrag}
                onStatus={changeStatus}
                onAssign={assign}
                onMove={move}
              />
            ))}
            {canDrag && <DropEnd onMove={move} />}
          </ul>
        </div>
      ))}
    </section>
  )
}

function IssueRow({
  issue,
  members,
  workflow,
  canEdit,
  canDrag,
  onStatus,
  onAssign,
  onMove,
}: {
  issue: Issue
  members: ProjectMember[]
  workflow: ReturnType<typeof useWorkflow>
  canEdit: boolean
  canDrag: boolean
  onStatus: (issue: Issue, status: IssueStatus) => void
  onAssign: (issue: Issue, assigneeId: string) => void
  onMove: (movedId: string, targetId: string | null) => void
}) {
  const [over, setOver] = useState(false)

  function dragStart(event: DragEvent) {
    event.dataTransfer.setData('text/plain', issue.id)
    event.dataTransfer.effectAllowed = 'move'
  }

  return (
    <li
      className={`issue-row ${over ? 'drop-target' : ''}`}
      onDragOver={(e) => {
        if (!canDrag) return
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        setOver(false)
        if (!canDrag) return
        e.preventDefault()
        onMove(e.dataTransfer.getData('text/plain'), issue.id)
      }}
    >
      {canDrag && (
        <span className="drag-handle" draggable onDragStart={dragStart} title="Kéo để sắp xếp" aria-label="Kéo để sắp xếp">
          ⠿
        </span>
      )}
      <div className="issue-main">
        <div className="issue-title">
          <span className="project-key">{issue.key}</span> <TypeBadge type={issue.type} />{' '}
          <Link to={`/issues/${issue.id}`}>{issue.title}</Link>
        </div>
        <div className="issue-meta muted small">
          {issue.blocked && <span className="blocked-chip" title={issue.blockedReason ?? ''}>Blocked</span>}
          {issue.sprint && issue.sprint.status !== 'COMPLETED' && <span className="sprint-chip">{issue.sprint.name}</span>}
          {issue.epic && issue.type !== 'EPIC' && <span>Epic: {issue.epic.title}</span>}
          {issue.parent && issue.parent.id !== issue.epic?.id && <span>Thuộc {issue.parent.key}</span>}
          {issue.childCount > 0 && (
            <span>
              {issue.childDoneCount}/{issue.childCount} việc con xong
            </span>
          )}
          {issue.labels.map((l) => (
            <span key={l} className="label-chip">
              {l}
            </span>
          ))}
        </div>
      </div>
      <PriorityBadge priority={issue.priority} />
      <span className="points" title="Story Points">
        {issue.storyPoints ?? '–'}
      </span>
      {canEdit ? (
        <select value={issue.status} onChange={(e) => onStatus(issue, e.target.value as IssueStatus)} aria-label="Trạng thái" className={`status-select status-${issue.status.toLowerCase()}`}>
          {workflow.enabled.map((s) => (
            <option key={s} value={s}>
              {workflow.names[s]}
            </option>
          ))}
        </select>
      ) : (
        <span className={`badge status-${issue.status.toLowerCase()}`}>{workflow.names[issue.status]}</span>
      )}
      {canEdit ? (
        <select value={issue.assignee?.id ?? ''} onChange={(e) => onAssign(issue, e.target.value)} aria-label="Người phụ trách" className="assignee-select">
          <option value="">Chưa giao</option>
          {members.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.fullName}
            </option>
          ))}
        </select>
      ) : (
        <Assignee user={issue.assignee} />
      )}
    </li>
  )
}

/** Drop zone below the last row, for moving an issue to the end. */
function DropEnd({ onMove }: { onMove: (movedId: string, targetId: string | null) => void }) {
  const [over, setOver] = useState(false)
  return (
    <li
      className={`drop-end ${over ? 'drop-target' : ''}`}
      onDragOver={(e) => {
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault()
        setOver(false)
        onMove(e.dataTransfer.getData('text/plain'), null)
      }}
    />
  )
}

function QuickCreate({ projectId, onCreated }: { projectId: string; onCreated: () => void }) {
  const [type, setType] = useState<IssueType>('TASK')
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!title.trim()) return
    setBusy(true)
    setError(null)
    try {
      await api(`/api/projects/${projectId}/issues`, { method: 'POST', body: { type, title } })
      setTitle('')
      onCreated()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="quick-create">
      <select value={type} onChange={(e) => setType(e.target.value as IssueType)} aria-label="Loại công việc">
        {QUICK_TYPES.map((t) => (
          <option key={t} value={t}>
            {TYPE_LABELS[t]}
          </option>
        ))}
      </select>
      <input placeholder="Thêm công việc vào cuối Backlog…" maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Tiêu đề" />
      <button type="submit" disabled={busy || !title.trim()}>
        Thêm
      </button>
      <Alert>{error}</Alert>
    </form>
  )
}
