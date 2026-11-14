import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import { smartspecProjectOf, smartspecProjectUrl } from '../api/smartspec'
import type {
  ChecklistItem,
  Issue,
  IssueActivity,
  IssueComment,
  IssueDetail,
  IssuePriority,
  IssueStatus,
  IssueType,
  Page,
  Project,
  ProjectMember,
  RefinementStatus,
} from '../api/types'
import { useResource } from '../api/useResource'
import { useWorkflow } from '../api/useWorkflow'
import { useAuth } from '../auth/AuthContext'
import { Alert, Field, TextArea } from '../components/Form'
import { PriorityBadge, TypeBadge } from '../components/IssueBits'
import { formatDay } from './roles'
import { RelatedDocuments } from './documents/RelatedDocuments'
import {
  ALLOWED_PARENTS,
  FIELD_LABELS,
  formatMinutes,
  historyValue,
  hoursToMinutes,
  ISSUE_PRIORITIES,
  ISSUE_TYPES,
  minutesToHours,
  PRIORITY_LABELS,
  REFINEMENT_LABELS,
  REFINEMENTS,
  TYPE_LABELS,
} from './issueLabels'
import { PageSkeleton } from '../components/Skeleton'
import { useLiveReload } from '../realtime/useLiveReload'
import { extractMentions } from './chat/mentions'

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN')
}

/** Issues created from a SmartSpec use case: the text is owned by SmartSpec, so say so and link back to it. */
function SpecSource({ externalRef }: { externalRef: string | null }) {
  const projectId = smartspecProjectOf(externalRef)
  if (!projectId) return null
  return (
    <p className="muted small">
      Tạo từ đặc tả SmartSpec · tiêu đề, mô tả và tiêu chí chấp nhận được SmartSpec cập nhật tự động.{' '}
      <a href={smartspecProjectUrl(projectId)} target="_blank" rel="noreferrer">
        Mở đặc tả ↗
      </a>
    </p>
  )
}

export function IssuePage() {
  const { issueId } = useParams()
  const { data: detail, error, reload } = useResource<IssueDetail>(`/api/issues/${issueId}`)
  const projectId = detail?.issue.projectId
  const { data: project } = useResource<Project>(projectId ? `/api/projects/${projectId}` : '/api/me')

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to="/workspaces">← Danh sách workspace</Link>
      </>
    )
  }
  if (!detail || !projectId || !project || !('key' in project)) return <PageSkeleton />

  return <IssueView key={detail.issue.id} detail={detail} project={project} reload={reload} />
}

function IssueView({ detail, project, reload }: { detail: IssueDetail; project: Project; reload: () => void }) {
  const { issue, children, checklist, permissions } = detail
  const navigate = useNavigate()
  // Comments and history load themselves; this counter makes them reload together with the issue.
  const [liveTick, setLiveTick] = useState(0)
  useLiveReload(project.id, ['issues'], () => {
    reload()
    setLiveTick((t) => t + 1)
  })
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${project.id}/members`)
  const workflow = useWorkflow(project.id)
  const active = project.status !== 'ARCHIVED'
  const canEdit = permissions.includes('EDIT_ISSUES') && active
  const canDelete = permissions.includes('MANAGE_BACKLOG') && active
  const canHaveChildren = ISSUE_TYPES.some((t) => ALLOWED_PARENTS[t].includes(issue.type))

  async function run(call: () => Promise<unknown>) {
    setError(null)
    try {
      await call()
      reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function remove() {
    if (!window.confirm(`Xóa ${issue.key}? Lịch sử sẽ được giữ nhưng công việc biến mất khỏi Backlog.`)) return
    try {
      await api(`/api/issues/${issue.id}`, { method: 'DELETE' })
      navigate(`/projects/${project.id}/backlog`)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <>
      <Link to={`/projects/${project.id}/backlog`} className="muted-link">
        ← Backlog · {project.key}
      </Link>
      <header className="issue-header">
        <div>
          <p className="muted small">
            <span className="project-key">{issue.key}</span> <TypeBadge type={issue.type} />
            {issue.parent && (
              <>
                {' '}
                · thuộc <Link to={`/issues/${issue.parent.id}`}>{issue.parent.key} {issue.parent.title}</Link>
              </>
            )}
          </p>
          <h1>{issue.title}</h1>
          <SpecSource externalRef={issue.externalRef} />
        </div>
        <div className="actions">
          {canEdit && (
            <button type="button" className="button-secondary" onClick={() => setEditing((v) => !v)}>
              {editing ? 'Đóng' : 'Chỉnh sửa'}
            </button>
          )}
          {canEdit && (
            <button
              type="button"
              className="button-secondary"
              onClick={() => {
                if (issue.blocked) return run(() => api(`/api/issues/${issue.id}/blocked`, { method: 'PUT', body: { blocked: false } }))
                const reason = window.prompt('Lý do bị chặn (không bắt buộc):')
                if (reason !== null) run(() => api(`/api/issues/${issue.id}/blocked`, { method: 'PUT', body: { blocked: true, reason } }))
              }}
            >
              {issue.blocked ? 'Bỏ chặn' : 'Đánh dấu Blocked'}
            </button>
          )}
          {canDelete && (
            <button type="button" className="button-danger" onClick={remove}>
              Xóa
            </button>
          )}
        </div>
      </header>
      {!active && <Alert kind="info">Dự án đã lưu trữ: chỉ xem.</Alert>}
      <Alert>{error}</Alert>

      <div className="two-column">
        <div className="stack-lg">
          {editing ? (
            <EditForm
              issue={issue}
              onSaved={() => {
                setEditing(false)
                reload()
              }}
            />
          ) : (
            <section className="card stack">
              <h2>Mô tả</h2>
              <p className="prewrap">{issue.description || <span className="muted">Chưa có mô tả.</span>}</p>
              {(issue.acceptanceCriteria || issue.type === 'USER_STORY') && (
                <>
                  <h2>Tiêu chí chấp nhận</h2>
                  <p className="prewrap">{issue.acceptanceCriteria || <span className="muted">Chưa có tiêu chí chấp nhận.</span>}</p>
                </>
              )}
            </section>
          )}

          {(canHaveChildren || children.length > 0) && (
            <ChildrenSection issue={issue} childIssues={children} names={workflow.names} canEdit={canEdit} onCreated={reload} />
          )}
          <Checklist issueId={issue.id} items={checklist} canEdit={canEdit} onChanged={reload} />
          <Comments liveTick={liveTick} issueId={issue.id} projectId={project.id} canComment={permissions.includes('COMMENT') && active} canModerate={permissions.includes('UPDATE_PROJECT')} />
        </div>

        <div className="stack-lg">
          <section className="card stack">
            <h2>Chi tiết</h2>
            <dl className="facts">
              <dt>Trạng thái</dt>
              <dd>
                {canEdit ? (
                  <select value={issue.status} aria-label="Trạng thái" onChange={(e) => run(() => api(`/api/issues/${issue.id}/status`, { method: 'PUT', body: { status: e.target.value as IssueStatus } }))}>
                    {workflow.enabled.map((s) => (
                      <option key={s} value={s}>
                        {workflow.names[s]}
                      </option>
                    ))}
                  </select>
                ) : (
                  workflow.names[issue.status]
                )}
                {issue.blocked && <span className="blocked-chip" title={issue.blockedReason ?? ''}>Blocked</span>}
              </dd>
              <dt>Người phụ trách</dt>
              <dd>
                {canEdit ? (
                  <select value={issue.assignee?.id ?? ''} aria-label="Người phụ trách" onChange={(e) => run(() => api(`/api/issues/${issue.id}/assignee`, { method: 'PUT', body: { assigneeId: e.target.value || null } }))}>
                    <option value="">Chưa giao</option>
                    {members?.map((m) => (
                      <option key={m.userId} value={m.userId}>
                        {m.fullName}
                      </option>
                    ))}
                  </select>
                ) : (
                  (issue.assignee?.fullName ?? 'Chưa giao')
                )}
              </dd>
              <dt>Sprint</dt>
              <dd>{issue.sprint ? <Link to={`/projects/${project.id}/sprints/${issue.sprint.id}`}>{issue.sprint.name}</Link> : 'Chưa vào Sprint'}</dd>
              <dt>Người báo cáo</dt>
              <dd>{issue.reporter.fullName}</dd>
              <dt>Độ ưu tiên</dt>
              <dd>
                <PriorityBadge priority={issue.priority} />
              </dd>
              <dt>Làm rõ</dt>
              <dd>{REFINEMENT_LABELS[issue.refinement]}</dd>
              <dt>Story Points</dt>
              <dd>{issue.storyPoints ?? '—'}</dd>
              <dt>Ước lượng ban đầu</dt>
              <dd>{formatMinutes(issue.originalEstimateMinutes)}</dd>
              <dt>Còn lại</dt>
              <dd>{formatMinutes(issue.remainingEstimateMinutes)}</dd>
              <dt>Bắt đầu</dt>
              <dd>{formatDay(issue.startDate)}</dd>
              <dt>Hạn</dt>
              <dd>{formatDay(issue.dueDate)}</dd>
              <dt>Epic</dt>
              <dd>{issue.epic ? <Link to={`/issues/${issue.epic.id}`}>{issue.epic.key} {issue.epic.title}</Link> : '—'}</dd>
              <dt>Nhãn</dt>
              <dd>{issue.labels.length ? issue.labels.map((l) => <span key={l} className="label-chip">{l}</span>) : '—'}</dd>
              <dt>Tạo lúc</dt>
              <dd>{formatDateTime(issue.createdAt)}</dd>
              <dt>Cập nhật</dt>
              <dd>{formatDateTime(issue.updatedAt)}</dd>
            </dl>
          </section>
          <RelatedDocuments projectId={issue.projectId} targetType="ISSUE" targetId={issue.id} />
          <History issueId={issue.id} version={issue.updatedAt} names={workflow.names} />

        </div>
      </div>
    </>
  )
}

function EditForm({ issue, onSaved }: { issue: Issue; onSaved: () => void }) {
  const [type, setType] = useState<IssueType>(issue.type)
  const [title, setTitle] = useState(issue.title)
  const [description, setDescription] = useState(issue.description ?? '')
  const [priority, setPriority] = useState<IssuePriority>(issue.priority)
  const [refinement, setRefinement] = useState<RefinementStatus>(issue.refinement)
  const [parentId, setParentId] = useState(issue.parent?.id ?? '')
  const [points, setPoints] = useState(issue.storyPoints?.toString() ?? '')
  const [original, setOriginal] = useState(minutesToHours(issue.originalEstimateMinutes))
  const [remaining, setRemaining] = useState(minutesToHours(issue.remainingEstimateMinutes))
  const [startDate, setStartDate] = useState(issue.startDate ?? '')
  const [dueDate, setDueDate] = useState(issue.dueDate ?? '')
  const [criteria, setCriteria] = useState(issue.acceptanceCriteria ?? '')
  const [labels, setLabels] = useState(issue.labels.join(', '))
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const parentTypes = ALLOWED_PARENTS[type]
  const parentQuery = parentTypes.map((t) => `type=${t}`).join('&')
  const { data: parents } = useResource<Page<Issue>>(
    parentTypes.length ? `/api/projects/${issue.projectId}/issues?${parentQuery}&size=200` : `/api/projects/${issue.projectId}/issues?size=1&type=EPIC`,
  )

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(`/api/issues/${issue.id}`, {
        method: 'PUT',
        body: {
          type,
          title,
          description,
          priority,
          refinement,
          parentId: parentTypes.length ? parentId || null : null,
          storyPoints: points === '' ? null : Number(points),
          originalEstimateMinutes: hoursToMinutes(original),
          remainingEstimateMinutes: hoursToMinutes(remaining),
          startDate: startDate || null,
          dueDate: dueDate || null,
          acceptanceCriteria: criteria,
          labels: labels.split(',').map((l) => l.trim()).filter(Boolean),
        },
      })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={save} className="card stack">
      <h2>Chỉnh sửa công việc</h2>
      <div className="inline-form">
        <label className="field">
          <span>Loại</span>
          <select value={type} onChange={(e) => setType(e.target.value as IssueType)}>
            {ISSUE_TYPES.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
        <Field label="Tiêu đề" required maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <TextArea label="Mô tả" maxLength={10000} value={description} onChange={(e) => setDescription(e.target.value)} />
      <TextArea label="Tiêu chí chấp nhận" maxLength={10000} value={criteria} onChange={(e) => setCriteria(e.target.value)} />
      <div className="inline-form">
        <label className="field">
          <span>Độ ưu tiên</span>
          <select value={priority} onChange={(e) => setPriority(e.target.value as IssuePriority)}>
            {ISSUE_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Làm rõ</span>
          <select value={refinement} onChange={(e) => setRefinement(e.target.value as RefinementStatus)}>
            {REFINEMENTS.map((r) => (
              <option key={r} value={r}>
                {REFINEMENT_LABELS[r]}
              </option>
            ))}
          </select>
        </label>
        <Field label="Story Points" type="number" min={0} max={100} step={1} value={points} onChange={(e) => setPoints(e.target.value)} />
      </div>
      {parentTypes.length > 0 && (
        <label className="field">
          <span>Công việc cha {type === 'SUB_TASK' ? '(bắt buộc)' : '(không bắt buộc)'}</span>
          <select value={parentId} required={type === 'SUB_TASK'} onChange={(e) => setParentId(e.target.value)}>
            <option value="">{type === 'SUB_TASK' ? 'Chọn công việc cha…' : 'Không có'}</option>
            {parents?.items.map((p) => (
              <option key={p.id} value={p.id}>
                {p.key} · {p.title} ({TYPE_LABELS[p.type]})
              </option>
            ))}
          </select>
        </label>
      )}
      <div className="inline-form">
        <Field label="Ước lượng ban đầu (giờ)" type="number" min={0} step={0.25} value={original} onChange={(e) => setOriginal(e.target.value)} />
        <Field label="Còn lại (giờ)" type="number" min={0} step={0.25} value={remaining} onChange={(e) => setRemaining(e.target.value)} />
      </div>
      <div className="inline-form">
        <Field label="Bắt đầu" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        <Field label="Hạn hoàn thành" type="date" min={startDate || undefined} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
      </div>
      <Field label="Nhãn (cách nhau bằng dấu phẩy, tối đa 10)" value={labels} onChange={(e) => setLabels(e.target.value)} />
      <Alert>{error}</Alert>
      <button type="submit" disabled={busy}>
        Lưu thay đổi
      </button>
    </form>
  )
}

function ChildrenSection({ issue, childIssues, names, canEdit, onCreated }: { issue: Issue; childIssues: Issue[]; names: Record<IssueStatus, string>; canEdit: boolean; onCreated: () => void }) {
  const childTypes = ISSUE_TYPES.filter((t) => ALLOWED_PARENTS[t].includes(issue.type))
  const [type, setType] = useState<IssueType>(childTypes[0])
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const done = childIssues.filter((c) => c.status === 'DONE').length

  async function add(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      await api(`/api/projects/${issue.projectId}/issues`, { method: 'POST', body: { type, title, parentId: issue.id } })
      setTitle('')
      onCreated()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card stack">
      <h2>
        Công việc con {childIssues.length > 0 && <span className="muted small">({done}/{childIssues.length} hoàn thành)</span>}
      </h2>
      {childIssues.length > 0 && (
        <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round((done / childIssues.length) * 100)}>
          <div className="progress-bar" style={{ width: `${(done / childIssues.length) * 100}%` }} />
        </div>
      )}
      <ul className="plain-list">
        {childIssues.map((c) => (
          <li key={c.id} className="row">
            <div>
              <span className="project-key">{c.key}</span> <TypeBadge type={c.type} /> <Link to={`/issues/${c.id}`}>{c.title}</Link>
            </div>
            <span className={`badge status-${c.status.toLowerCase()}`}>{names[c.status]}</span>
          </li>
        ))}
      </ul>
      {canEdit && childTypes.length > 0 && (
        <form onSubmit={add} className="quick-create">
          <select value={type} onChange={(e) => setType(e.target.value as IssueType)} aria-label="Loại">
            {childTypes.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
          <input placeholder="Thêm công việc con…" maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Tiêu đề" />
          <button type="submit" disabled={!title.trim()}>
            Thêm
          </button>
        </form>
      )}
      <Alert>{error}</Alert>
    </section>
  )
}

function Checklist({ issueId, items, canEdit, onChanged }: { issueId: string; items: ChecklistItem[]; canEdit: boolean; onChanged: () => void }) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const done = items.filter((i) => i.done).length

  async function run(call: () => Promise<unknown>) {
    setError(null)
    try {
      await call()
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (!canEdit && items.length === 0) return null
  return (
    <section className="card stack">
      <h2>
        Checklist {items.length > 0 && <span className="muted small">({done}/{items.length})</span>}
      </h2>
      <ul className="plain-list">
        {items.map((item) => (
          <li key={item.id} className="row">
            <label className={`inline-check ${item.done ? 'struck' : ''}`}>
              <input type="checkbox" checked={item.done} disabled={!canEdit} onChange={(e) => run(() => api(`/api/checklist/${item.id}`, { method: 'PUT', body: { done: e.target.checked } }))} /> {item.text}
            </label>
            {canEdit && (
              <button type="button" className="button-secondary button-small" onClick={() => run(() => api(`/api/checklist/${item.id}`, { method: 'DELETE' }))}>
                Xóa
              </button>
            )}
          </li>
        ))}
      </ul>
      {canEdit && (
        <form
          className="quick-create"
          onSubmit={(e) => {
            e.preventDefault()
            run(async () => {
              await api(`/api/issues/${issueId}/checklist`, { method: 'POST', body: { text } })
              setText('')
            })
          }}
        >
          <input placeholder="Thêm mục checklist…" maxLength={500} value={text} onChange={(e) => setText(e.target.value)} aria-label="Mục checklist" />
          <button type="submit" disabled={!text.trim()}>
            Thêm
          </button>
        </form>
      )}
      <Alert>{error}</Alert>
    </section>
  )
}

function Comments({ issueId, projectId, liveTick, canComment, canModerate }: { issueId: string; projectId: string; liveTick: number; canComment: boolean; canModerate: boolean }) {
  const { user } = useAuth()
  const { data: comments, error: loadError, reload } = useResource<IssueComment[]>(`/api/issues/${issueId}/comments?v=${liveTick}`)
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${projectId}/members`)
  const [body, setBody] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editBody, setEditBody] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function run(call: () => Promise<unknown>) {
    setError(null)
    try {
      await call()
      reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card stack">
      <h2>Bình luận {comments && <span className="muted small">({comments.length})</span>}</h2>
      <Alert>{loadError ?? error}</Alert>
      <ul className="plain-list">
        {comments?.map((c) => (
          <li key={c.id} className="comment">
            <p className="small">
              <strong>{c.author.fullName}</strong> <span className="muted">{formatDateTime(c.createdAt)}{c.updatedAt !== c.createdAt && ' (đã sửa)'}</span>
            </p>
            {editingId === c.id ? (
              <form
                className="stack"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(async () => {
                    await api(`/api/comments/${c.id}`, { method: 'PUT', body: { body: editBody } })
                    setEditingId(null)
                  })
                }}
              >
                <TextArea label="Sửa bình luận" maxLength={5000} value={editBody} onChange={(e) => setEditBody(e.target.value)} />
                <div className="actions">
                  <button type="submit">Lưu</button>
                  <button type="button" className="button-secondary" onClick={() => setEditingId(null)}>
                    Hủy
                  </button>
                </div>
              </form>
            ) : (
              <p className="prewrap">{c.body}</p>
            )}
            {editingId !== c.id && canComment && (
              <div className="actions">
                {c.author.id === user?.id && (
                  <button
                    type="button"
                    className="link-button small"
                    onClick={() => {
                      setEditingId(c.id)
                      setEditBody(c.body)
                    }}
                  >
                    Sửa
                  </button>
                )}
                {(c.author.id === user?.id || canModerate) && (
                  <button type="button" className="link-button small" onClick={() => window.confirm('Xóa bình luận này?') && run(() => api(`/api/comments/${c.id}`, { method: 'DELETE' }))}>
                    Xóa
                  </button>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
      {canComment && (
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault()
            run(async () => {
              const people = (members ?? []).map((m) => ({ id: m.userId, fullName: m.fullName, avatarUrl: m.avatarUrl }))
              await api(`/api/issues/${issueId}/comments`, { method: 'POST', body: { body, mentions: extractMentions(body, people, user?.id) } })
              setBody('')
            })
          }}
        >
          <TextArea label="Viết bình luận" maxLength={5000} value={body} onChange={(e) => setBody(e.target.value)} />
          <div className="actions">
            <button type="submit" disabled={!body.trim()}>
              Gửi bình luận
            </button>
            <select
              value=""
              aria-label="Nhắc đến một thành viên"
              onChange={(e) => {
                const member = members?.find((m) => m.userId === e.target.value)
                if (member) setBody((b) => `${b}${b && !b.endsWith(' ') ? ' ' : ''}@${member.fullName} `)
              }}
            >
              <option value="">Nhắc đến…</option>
              {members
                ?.filter((m) => m.userId !== user?.id)
                .map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.fullName}
                  </option>
                ))}
            </select>
          </div>
        </form>
      )}
    </section>
  )
}

/** `version` (the issue's updatedAt) changes whenever the issue does, which reloads the list. */
function History({ issueId, version, names }: { issueId: string; version: string; names: Record<IssueStatus, string> }) {
  const { data: entries, error } = useResource<IssueActivity[]>(`/api/issues/${issueId}/activity?v=${encodeURIComponent(version)}`)
  return (
    <section className="card stack">
      <h2>Lịch sử thay đổi</h2>
      <Alert>{error}</Alert>
      <ul className="plain-list">
        {entries?.map((e) => (
          <li key={e.id} className="small history-item">
            <strong>{e.actor.fullName}</strong>{' '}
            {e.kind === 'CREATED' ? (
              'đã tạo công việc'
            ) : (
              <>
                đã đổi {FIELD_LABELS[e.field ?? ''] ?? e.field}
                {e.oldValue === null && e.newValue === null ? '' : ` từ “${historyValue(e.field, e.oldValue, names)}” sang “${historyValue(e.field, e.newValue, names)}”`}
              </>
            )}
            <span className="muted"> · {formatDateTime(e.createdAt)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
