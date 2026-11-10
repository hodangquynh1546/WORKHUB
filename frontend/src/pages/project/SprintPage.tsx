import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { GoalOutcome, Issue, Page, ProjectMember, Sprint, SprintDetail, SprintReport } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useWorkflow } from '../../api/useWorkflow'
import { Alert, Field, TextArea } from '../../components/Form'
import { PriorityBadge, TypeBadge } from '../../components/IssueBits'
import { GOAL_OUTCOME_LABELS, REFINEMENT_LABELS, SPRINT_STATUS_LABELS } from '../issueLabels'
import { formatDay, localIsoDate, scheduleProgress } from '../roles'
import { useProject } from './projectContext'

export function SprintPage() {
  const { sprintId } = useParams()
  const { project } = useProject()
  const [today] = useState(() => localIsoDate())
  const { data: detail, error, reload } = useResource<SprintDetail>(`/api/sprints/${sprintId}?today=${today}`)

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to={`/projects/${project.id}/sprints`}>← Danh sách Sprint</Link>
      </>
    )
  }
  if (!detail) return <p className="muted">Đang tải…</p>

  const { sprint, dashboard, issues } = detail
  const open = sprint.status === 'FUTURE' || sprint.status === 'ACTIVE'
  const canManage = detail.permissions.includes('MANAGE_SPRINTS') && project.status !== 'ARCHIVED'

  return (
    <div className="stack-lg">
      <Link to={`/projects/${project.id}/sprints`} className="muted-link">
        ← Danh sách Sprint
      </Link>
      <SprintHeader detail={detail} canManage={canManage} onChanged={reload} />
      {sprint.status !== 'FUTURE' && <Dashboard detail={detail} />}
      {open && <CapacityBar sprint={sprint} scope={dashboard.scopePoints} />}
      {sprint.status === 'ACTIVE' && canManage && <CompletePanel detail={detail} projectId={project.id} onDone={reload} />}
      <div className={open && canManage ? 'two-column' : ''}>
        <SprintIssues sprint={sprint} issues={issues} canManage={canManage && open} onChanged={reload} />
        {open && canManage && <Picker sprintId={sprint.id} projectId={project.id} onAdded={reload} />}
      </div>
      {sprint.status === 'COMPLETED' && <Report sprintId={sprint.id} />}
    </div>
  )
}

function SprintHeader({ detail, canManage, onChanged }: { detail: SprintDetail; canManage: boolean; onChanged: () => void }) {
  const { sprint } = detail
  const { project } = useProject()
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const progress = sprint.status === 'ACTIVE' ? scheduleProgress(sprint.startDate, sprint.endDate) : null
  const open = sprint.status === 'FUTURE' || sprint.status === 'ACTIVE'

  async function run(call: () => Promise<unknown>) {
    setError(null)
    try {
      await call()
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card stack">
      <div className="section-header">
        <div>
          <h1>
            {sprint.name} <span className={`badge sprint-${sprint.status.toLowerCase()}`}>{SPRINT_STATUS_LABELS[sprint.status]}</span>
          </h1>
          <p className="muted">
            {formatDay(sprint.startDate)} → {formatDay(sprint.endDate)}
            {sprint.facilitator && ` · điều phối: ${sprint.facilitator.fullName}`}
          </p>
        </div>
        {canManage && open && (
          <div className="actions">
            {sprint.status === 'FUTURE' && (
              <button type="button" onClick={() => run(() => api(`/api/sprints/${sprint.id}/start`, { method: 'POST' }))}>
                Bắt đầu Sprint
              </button>
            )}
            {sprint.status === 'ACTIVE' && (
              <Link className="button-link button-link-secondary" to={`/projects/${project.id}/board`}>
                Mở Board
              </Link>
            )}
            <button type="button" className="button-secondary" onClick={() => setEditing((v) => !v)}>
              {editing ? 'Đóng' : 'Chỉnh sửa'}
            </button>
            <button
              type="button"
              className="button-danger"
              onClick={() => window.confirm('Hủy Sprint này? Mọi công việc sẽ quay về Backlog.') && run(() => api(`/api/sprints/${sprint.id}/cancel`, { method: 'POST' }))}
            >
              Hủy Sprint
            </button>
          </div>
        )}
      </div>
      {sprint.goal ? (
        <p>
          🎯 <strong>Mục tiêu:</strong> {sprint.goal}
        </p>
      ) : (
        open && <p className="muted small">Chưa đặt mục tiêu Sprint. Scrum khuyến khích có một mục tiêu rõ ràng trước khi bắt đầu.</p>
      )}
      {progress && (
        <>
          <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.percent}>
            <div className="progress-bar" style={{ width: `${progress.percent}%` }} />
          </div>
          <p className="muted small">
            {progress.finished ? 'Đã qua ngày kết thúc dự kiến.' : `Ngày ${progress.elapsedDays}/${progress.totalDays} · còn ${progress.remainingDays} ngày.`}
          </p>
        </>
      )}
      <Alert>{error}</Alert>
      {editing && (
        <EditSprint
          sprint={sprint}
          onSaved={() => {
            setEditing(false)
            onChanged()
          }}
        />
      )}
    </section>
  )
}

function EditSprint({ sprint, onSaved }: { sprint: Sprint; onSaved: () => void }) {
  const { project } = useProject()
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${project.id}/members`)
  const [name, setName] = useState(sprint.name)
  const [goal, setGoal] = useState(sprint.goal ?? '')
  const [startDate, setStartDate] = useState(sprint.startDate)
  const [endDate, setEndDate] = useState(sprint.endDate)
  const [facilitatorId, setFacilitatorId] = useState(sprint.facilitator?.id ?? '')
  const [capacity, setCapacity] = useState(sprint.capacityPoints?.toString() ?? '')
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/sprints/${sprint.id}`, {
        method: 'PUT',
        body: { name, goal, startDate, endDate, facilitatorId: facilitatorId || null, capacityPoints: capacity === '' ? null : Number(capacity) },
      })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <form onSubmit={save} className="stack edit-box">
      <Field label="Tên Sprint" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      <TextArea label="Mục tiêu Sprint" rows={2} maxLength={1000} value={goal} onChange={(e) => setGoal(e.target.value)} />
      <div className="inline-form">
        <Field label="Bắt đầu" type="date" required disabled={sprint.status === 'ACTIVE'} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        <Field label="Kết thúc" type="date" required min={startDate} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        <Field label="Năng lực (SP)" type="number" min={0} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
      </div>
      <label className="field">
        <span>Người điều phối</span>
        <select value={facilitatorId} onChange={(e) => setFacilitatorId(e.target.value)}>
          <option value="">Chưa chọn</option>
          {members?.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.fullName}
            </option>
          ))}
        </select>
      </label>
      <Alert>{error}</Alert>
      <button type="submit">Lưu</button>
    </form>
  )
}

function Dashboard({ detail }: { detail: SprintDetail }) {
  const { sprint, dashboard: d } = detail
  const closed = sprint.status === 'COMPLETED'
  return (
    <section className="card stack">
      <h2>{closed ? 'Kết quả Sprint' : 'Bảng điều khiển Sprint'}</h2>
      <div className="stat-grid">
        <Stat label="Tổng công việc" value={d.totalIssues} />
        <Stat label="Cần làm" value={d.todoIssues} />
        <Stat label="Đang làm" value={d.inProgressIssues} />
        <Stat label="Hoàn thành" value={d.doneIssues} />
        {!closed && <Stat label="Bị chặn" value={d.blockedIssues} warn={d.blockedIssues > 0} />}
        {!closed && <Stat label="Quá hạn" value={d.overdueIssues} warn={d.overdueIssues > 0} />}
        <Stat label="Story Points xong" value={`${d.donePoints}/${d.scopePoints}`} />
        <Stat label="Còn lại (SP)" value={d.remainingPoints} />
      </div>
      <p className="muted small">
        Cam kết ban đầu: {d.committedPoints ?? '—'} SP. Sau khi bắt đầu: thêm {d.addedIssues} việc (+{d.addedPoints} SP), bớt {d.removedIssues} việc (−{d.removedPoints} SP).
        Hoàn thành khối lượng công việc chưa chắc đã đạt mục tiêu Sprint.
      </p>
    </section>
  )
}

function Stat({ label, value, warn = false }: { label: string; value: number | string; warn?: boolean }) {
  return (
    <div className={`stat ${warn ? 'stat-warn' : ''}`}>
      <strong>{value}</strong>
      <span className="muted small">{label}</span>
    </div>
  )
}

function CapacityBar({ sprint, scope }: { sprint: Sprint; scope: number }) {
  if (sprint.capacityPoints === null) {
    return <p className="muted small">Khối lượng hiện tại: {scope} SP. Đặt “Năng lực” để so sánh với khả năng của team.</p>
  }
  const percent = sprint.capacityPoints === 0 ? 100 : Math.min(100, Math.round((scope / sprint.capacityPoints) * 100))
  const over = scope > sprint.capacityPoints
  return (
    <section className="card stack">
      <h2>Khối lượng so với năng lực</h2>
      <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
        <div className={`progress-bar ${over ? 'progress-over' : ''}`} style={{ width: `${percent}%` }} />
      </div>
      <p className={over ? 'text-danger' : 'muted'}>
        {scope}/{sprint.capacityPoints} SP{over ? ` · vượt năng lực ${scope - sprint.capacityPoints} SP` : ''}
      </p>
    </section>
  )
}

function SprintIssues({ sprint, issues, canManage, onChanged }: { sprint: Sprint; issues: Issue[]; canManage: boolean; onChanged: () => void }) {
  const { project } = useProject()
  const workflow = useWorkflow(project.id)
  const [error, setError] = useState<string | null>(null)

  async function remove(issue: Issue, includeChildren: boolean) {
    setError(null)
    try {
      await api(`/api/sprints/${sprint.id}/issues/${issue.id}?includeChildren=${includeChildren}`, { method: 'DELETE' })
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card stack">
      <h2>Sprint Backlog ({issues.length})</h2>
      <Alert>{error}</Alert>
      {issues.length === 0 && <p className="muted">{canManage ? 'Chọn công việc ở bên phải để đưa vào Sprint.' : 'Sprint không có công việc.'}</p>}
      <ul className="plain-list">
        {issues.map((issue) => (
          <li key={issue.id} className="row">
            <div className="issue-main">
              <div className="issue-title">
                <span className="project-key">{issue.key}</span> <TypeBadge type={issue.type} /> <Link to={`/issues/${issue.id}`}>{issue.title}</Link>
                {issue.blocked && <span className="blocked-chip" title={issue.blockedReason ?? ''}>Blocked</span>}
              </div>
              <div className="issue-meta muted small">
                <span>{workflow.names[issue.status]}</span>
                {issue.assignee && <span>{issue.assignee.fullName}</span>}
              </div>
            </div>
            <PriorityBadge priority={issue.priority} />
            <span className="points" title="Story Points">
              {issue.storyPoints ?? '–'}
            </span>
            {canManage && (
              <button type="button" className="button-secondary button-small" onClick={() => remove(issue, issue.childCount > 0)}>
                Bỏ khỏi Sprint
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Backlog items not yet in a sprint, to pick from. */
function Picker({ sprintId, projectId, onAdded }: { sprintId: string; projectId: string; onAdded: () => void }) {
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [includeChildren, setIncludeChildren] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const types = ['FEATURE', 'USER_STORY', 'TASK', 'SUB_TASK', 'BUG', 'SPIKE']
  const statuses = ['TO_DO', 'IN_PROGRESS', 'CODE_REVIEW', 'TESTING']
  const path =
    `/api/projects/${projectId}/issues?unplanned=true&size=200&` +
    [...types.map((t) => `type=${t}`), ...statuses.map((s) => `status=${s}`)].join('&') +
    (q.trim() ? `&q=${encodeURIComponent(q.trim())}` : '')
  const { data, reload } = useResource<Page<Issue>>(path)

  const toggle = (id: string) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  async function add() {
    setError(null)
    try {
      await api(`/api/sprints/${sprintId}/issues`, { method: 'POST', body: { issueIds: [...selected], includeChildren } })
      setSelected(new Set())
      reload()
      onAdded()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card stack">
      <h2>Backlog chưa lên kế hoạch</h2>
      <input type="search" placeholder="Tìm theo tiêu đề hoặc mã…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Tìm kiếm" />
      <label className="inline-check">
        <input type="checkbox" checked={includeChildren} onChange={(e) => setIncludeChildren(e.target.checked)} /> Thêm cả công việc con chưa xong
      </label>
      <Alert>{error}</Alert>
      {data?.items.length === 0 && <p className="muted">Không còn công việc nào để thêm.</p>}
      <ul className="plain-list picker-list">
        {data?.items.map((issue) => (
          <li key={issue.id} className="row">
            <label className="inline-check">
              <input type="checkbox" checked={selected.has(issue.id)} onChange={() => toggle(issue.id)} />
              <span>
                <span className="project-key">{issue.key}</span> {issue.title}
                <span className="muted small"> · {REFINEMENT_LABELS[issue.refinement]}</span>
              </span>
            </label>
            <span className="points">{issue.storyPoints ?? '–'}</span>
          </li>
        ))}
      </ul>
      <button type="button" disabled={selected.size === 0} onClick={add}>
        Thêm {selected.size > 0 ? `${selected.size} ` : ''}vào Sprint
      </button>
    </section>
  )
}

function CompletePanel({ detail, projectId, onDone }: { detail: SprintDetail; projectId: string; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const unfinished = detail.dashboard.totalIssues - detail.dashboard.doneIssues
  return (
    <section className="card stack">
      <div className="section-header">
        <h2>Hoàn thành Sprint</h2>
        <button type="button" className="button-secondary button-small" onClick={() => setOpen((v) => !v)}>
          {open ? 'Đóng' : 'Hoàn thành Sprint…'}
        </button>
      </div>
      {open && <CompleteForm sprint={detail.sprint} unfinished={unfinished} projectId={projectId} onDone={onDone} />}
    </section>
  )
}

function CompleteForm({ sprint, unfinished, projectId, onDone }: { sprint: Sprint; unfinished: number; projectId: string; onDone: () => void }) {
  const { data: future } = useResource<Sprint[]>(`/api/projects/${projectId}/sprints?status=FUTURE`)
  const [outcome, setOutcome] = useState<GoalOutcome>('ACHIEVED')
  const [note, setNote] = useState('')
  const [destination, setDestination] = useState<'BACKLOG' | 'NEXT_SPRINT'>('BACKLOG')
  const [nextId, setNextId] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/sprints/${sprint.id}/complete`, {
        method: 'POST',
        body: { goalOutcome: outcome, goalNote: note, unfinishedTo: destination, nextSprintId: destination === 'NEXT_SPRINT' ? nextId : null },
      })
      onDone()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <form onSubmit={submit} className="stack">
      <p className="muted small">Chỉ công việc ở trạng thái Hoàn thành mới được tính là xong. Mục tiêu Sprint do team đánh giá, không tự suy ra từ số công việc.</p>
      <fieldset className="stack radio-group">
        <legend>Mục tiêu Sprint có đạt không?</legend>
        {(Object.keys(GOAL_OUTCOME_LABELS) as GoalOutcome[]).map((value) => (
          <label key={value} className="inline-check">
            <input type="radio" name="outcome" checked={outcome === value} onChange={() => setOutcome(value)} /> {GOAL_OUTCOME_LABELS[value]}
          </label>
        ))}
      </fieldset>
      <TextArea label="Ghi chú (không bắt buộc)" rows={2} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />
      {unfinished > 0 && (
        <>
          <label className="field">
            <span>{unfinished} công việc chưa xong sẽ…</span>
            <select value={destination} onChange={(e) => setDestination(e.target.value as 'BACKLOG' | 'NEXT_SPRINT')}>
              <option value="BACKLOG">Quay về Backlog</option>
              <option value="NEXT_SPRINT" disabled={!future?.length}>
                Chuyển sang Sprint kế tiếp
              </option>
            </select>
          </label>
          {destination === 'NEXT_SPRINT' && (
            <label className="field">
              <span>Sprint kế tiếp</span>
              <select required value={nextId} onChange={(e) => setNextId(e.target.value)}>
                <option value="">Chọn Sprint…</option>
                {future?.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({formatDay(s.startDate)})
                  </option>
                ))}
              </select>
            </label>
          )}
        </>
      )}
      <Alert>{error}</Alert>
      <button type="submit">Hoàn thành Sprint</button>
    </form>
  )
}

function Report({ sprintId }: { sprintId: string }) {
  const { data: report, error } = useResource<SprintReport>(`/api/sprints/${sprintId}/report`)
  if (error) return <Alert>{error}</Alert>
  if (!report) return null
  const { sprint } = report
  const list = (title: string, issues: Issue[], empty: string) => (
    <section className="card stack">
      <h2>
        {title} ({issues.length})
      </h2>
      {issues.length === 0 && <p className="muted">{empty}</p>}
      <ul className="plain-list">
        {issues.map((i) => (
          <li key={i.id} className="row">
            <div>
              <span className="project-key">{i.key}</span> <Link to={`/issues/${i.id}`}>{i.title}</Link>
            </div>
            <span className="points">{i.storyPoints ?? '–'}</span>
          </li>
        ))}
      </ul>
    </section>
  )
  return (
    <div className="stack-lg">
      <section className="card stack">
        <h2>Báo cáo Sprint</h2>
        <dl className="facts">
          <dt>Mục tiêu</dt>
          <dd>{sprint.goalOutcome ? GOAL_OUTCOME_LABELS[sprint.goalOutcome] : '—'}</dd>
          {sprint.goalNote && (
            <>
              <dt>Ghi chú</dt>
              <dd className="prewrap">{sprint.goalNote}</dd>
            </>
          )}
          <dt>Cam kết</dt>
          <dd>
            {sprint.committedIssues ?? 0} việc · {sprint.committedPoints ?? 0} SP
          </dd>
          <dt>Hoàn thành</dt>
          <dd>
            {sprint.completedIssues ?? 0} việc · {sprint.completedPoints ?? 0} SP
          </dd>
          <dt>Thay đổi phạm vi</dt>
          <dd>
            +{report.dashboard.addedIssues} việc (+{report.dashboard.addedPoints} SP) / −{report.dashboard.removedIssues} việc (−{report.dashboard.removedPoints} SP)
          </dd>
        </dl>
      </section>
      {list('Đã hoàn thành', report.doneIssues, 'Không có công việc nào hoàn thành.')}
      {list('Chưa hoàn thành (đã chuyển đi)', report.unfinishedIssues, 'Mọi công việc đã hoàn thành.')}
      {report.removedIssues.length > 0 && list('Đã bỏ khỏi Sprint', report.removedIssues, '')}
      {report.snapshots.length > 0 && (
        <section className="card stack">
          <h2>Số liệu theo ngày</h2>
          <p className="muted small">Dữ liệu này là nền cho biểu đồ Burndown/Burnup (sẽ có ở giai đoạn sau).</p>
          <table className="table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Phạm vi (SP)</th>
                <th>Xong (SP)</th>
                <th>Còn lại (SP)</th>
                <th>Việc xong</th>
              </tr>
            </thead>
            <tbody>
              {report.snapshots.map((s) => (
                <tr key={s.date}>
                  <td>{formatDay(s.date)}</td>
                  <td>{s.scopePoints}</td>
                  <td>{s.donePoints}</td>
                  <td>{s.scopePoints - s.donePoints}</td>
                  <td>
                    {s.doneIssues}/{s.scopeIssues}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  )
}
