import { useEffect, useMemo, useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { Board, Issue, IssueStatus, IssueType } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Alert } from '../../components/Form'
import { Assignee, PriorityBadge, TypeBadge } from '../../components/IssueBits'
import { ISSUE_TYPES, TYPE_LABELS } from '../issueLabels'
import { formatDay, scheduleProgress } from '../roles'
import { useProject } from './projectContext'
import { PageSkeleton } from '../../components/Skeleton'

export function BoardPage() {
  const { project } = useProject()
  const { user } = useAuth()
  const [board, setBoard] = useState<Board | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [onlyMine, setOnlyMine] = useState(false)
  const [type, setType] = useState<IssueType | ''>('')
  const [dragOver, setDragOver] = useState<IssueStatus | null>(null)
  const [dragId, setDragId] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api<Board>(`/api/projects/${project.id}/board`)
      .then((data) => !cancelled && (setBoard(data), setError(null)))
      .catch((err) => !cancelled && setError(errorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [project.id, version])

  const reload = () => setVersion((v) => v + 1)
  const canEdit = !!board?.permissions.includes('EDIT_ISSUES') && project.status !== 'ARCHIVED'

  const visible = useMemo(
    () => (board?.issues ?? []).filter((i) => (!onlyMine || i.assignee?.id === user?.id) && (!type || i.type === type)),
    [board, onlyMine, type, user],
  )

  async function mutate(call: () => Promise<unknown>) {
    setError(null)
    try {
      await call()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      reload()
    }
  }

  function moveTo(status: IssueStatus, event: DragEvent) {
    event.preventDefault()
    setDragOver(null)
    const id = event.dataTransfer.getData('text/plain')
    const issue = board?.issues.find((i) => i.id === id)
    if (!canEdit || !issue || issue.status === status) return
    // Show the card in its new column straight away; the reload settles on the server's answer.
    setBoard((b) => (b ? { ...b, issues: b.issues.map((i) => (i.id === id ? { ...i, status } : i)) } : b))
    mutate(() => api(`/api/issues/${id}/status`, { method: 'PUT', body: { status } }))
  }

  function toggleBlocked(issue: Issue) {
    if (issue.blocked) return mutate(() => api(`/api/issues/${issue.id}/blocked`, { method: 'PUT', body: { blocked: false } }))
    const reason = window.prompt('Lý do bị chặn (không bắt buộc):')
    if (reason === null) return
    return mutate(() => api(`/api/issues/${issue.id}/blocked`, { method: 'PUT', body: { blocked: true, reason } }))
  }

  if (error && !board) return <Alert>{error}</Alert>
  if (!board) return <PageSkeleton />

  const scrum = board.methodology === 'SCRUM'
  if (scrum && !board.sprint) {
    return (
      <section className="card stack">
        <h2>Scrum Board</h2>
        <p className="muted">Hiện không có Sprint nào đang chạy nên Board trống.</p>
        <Link to={`/projects/${project.id}/sprints`}>Đến trang Sprint để lên kế hoạch và bắt đầu →</Link>
      </section>
    )
  }

  const sprint = board.sprint
  const progress = sprint ? scheduleProgress(sprint.startDate, sprint.endDate) : null

  return (
    <div className="stack-lg">
      {sprint && (
        <section className="card stack">
          <div className="section-header">
            <h2>
              <Link to={`/projects/${project.id}/sprints/${sprint.id}`}>{sprint.name}</Link>{' '}
              <span className="muted small">
                {formatDay(sprint.startDate)} → {formatDay(sprint.endDate)}
                {progress && !progress.finished && ` · còn ${progress.remainingDays} ngày`}
              </span>
            </h2>
            <span className="muted small">
              {sprint.donePoints}/{sprint.scopePoints} SP xong
            </span>
          </div>
          {sprint.goal && <p>🎯 {sprint.goal}</p>}
        </section>
      )}
      {!scrum && <p className="muted small">Kanban: hiện công việc đang mở và công việc vừa hoàn thành trong 14 ngày qua.</p>}

      <div className="filters">
        <label className="inline-check">
          <input type="checkbox" checked={onlyMine} onChange={(e) => setOnlyMine(e.target.checked)} /> Chỉ việc của tôi
        </label>
        <select value={type} onChange={(e) => setType(e.target.value as IssueType | '')} aria-label="Loại">
          <option value="">Mọi loại</option>
          {ISSUE_TYPES.filter((t) => t !== 'EPIC').map((t) => (
            <option key={t} value={t}>
              {TYPE_LABELS[t]}
            </option>
          ))}
        </select>
      </div>
      <Alert>{error}</Alert>

      <div className="board" style={{ gridTemplateColumns: `repeat(${board.columns.length}, minmax(220px, 1fr))` }}>
        {board.columns.map((column) => {
          const cards = visible.filter((i) => i.status === column.status)
          const points = cards.reduce((sum, i) => sum + (i.storyPoints ?? 0), 0)
          return (
            <section
              key={column.status}
              className={`board-column ${dragOver === column.status ? 'drop-target-column' : ''}`}
              onDragOver={(e) => {
                if (!canEdit) return
                e.preventDefault()
                setDragOver(column.status)
              }}
              onDragLeave={() => setDragOver(null)}
              onDrop={(e) => moveTo(column.status, e)}
            >
              <h3>
                {column.name} <span className="muted small">{cards.length} · {points} SP</span>
              </h3>
              {cards.map((issue) => (
                <article
                  key={issue.id}
                  className={`board-card ${issue.blocked ? 'board-card-blocked' : ''} ${dragId === issue.id ? 'board-card-dragging' : ''}`}
                  draggable={canEdit}
                  onDragStart={(e) => {
                    e.dataTransfer.setData('text/plain', issue.id)
                    e.dataTransfer.effectAllowed = 'move'
                    setDragId(issue.id)
                  }}
                  onDragEnd={() => {
                    setDragId(null)
                    setDragOver(null)
                  }}
                >
                  <div className="issue-title">
                    <span className="project-key">{issue.key}</span> <TypeBadge type={issue.type} />
                  </div>
                  <Link to={`/issues/${issue.id}`} className="board-card-title">
                    {issue.title}
                  </Link>
                  {issue.parent && <p className="muted small">{issue.parent.key} · {issue.parent.title}</p>}
                  {issue.blocked && <p className="blocked-chip" title={issue.blockedReason ?? ''}>Blocked{issue.blockedReason ? `: ${issue.blockedReason}` : ''}</p>}
                  <div className="board-card-footer">
                    <PriorityBadge priority={issue.priority} />
                    <span className="points">{issue.storyPoints ?? '–'}</span>
                    {issue.dueDate && <span className="muted small">{formatDay(issue.dueDate)}</span>}
                    <Assignee user={issue.assignee} />
                    {canEdit && (
                      <button type="button" className="link-button small" onClick={() => toggleBlocked(issue)}>
                        {issue.blocked ? 'Bỏ chặn' : 'Chặn'}
                      </button>
                    )}
                  </div>
                </article>
              ))}
              {cards.length === 0 && <p className="muted small column-empty">Không có công việc</p>}
            </section>
          )
        })}
      </div>
    </div>
  )
}
