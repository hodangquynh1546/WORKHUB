import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ActionItem, AttendeeResponse, MeetingDetail, MeetingTemplate, ProjectMember } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'
import { Avatar } from '../../components/Avatar'
import { Markdown } from '../../components/Markdown'
import { PageSkeleton } from '../../components/Skeleton'
import { RelatedDocuments } from '../documents/RelatedDocuments'
import { formatDay } from '../roles'
import { useProject } from '../project/projectContext'
import { MeetingForm } from './MeetingForm'
import { MEETING_TYPE_LABELS, REMINDER_OPTIONS, RESPONSE_LABELS, formatRange } from './labels'

export function MeetingPage() {
  const { meetingId } = useParams()
  const { project } = useProject()
  const navigate = useNavigate()
  const { data: meeting, error, reload } = useResource<MeetingDetail>(`/api/meetings/${meetingId}`)
  const [editing, setEditing] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to={`/projects/${project.id}/meetings`}>← Danh sách cuộc họp</Link>
      </>
    )
  }
  if (!meeting) return <PageSkeleton />

  async function run(action: () => Promise<unknown>) {
    setActionError(null)
    try {
      await action()
      reload()
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  const cancelled = meeting.status === 'CANCELLED'
  return (
    <>
      <Link to={`/projects/${project.id}/meetings`} className="muted-link">
        ← Cuộc họp
      </Link>
      <header className="issue-header">
        <div>
          <div className="doc-card-title">
            <span className="badge">{MEETING_TYPE_LABELS[meeting.type]}</span>
            {cancelled && <span className="badge badge-muted">Đã hủy</span>}
            {meeting.sprint && (
              <Link to={`/projects/${project.id}/sprints/${meeting.sprint.id}`} className="sprint-chip">
                {meeting.sprint.name}
              </Link>
            )}
          </div>
          <h1 className={cancelled ? 'struck' : ''}>{meeting.title}</h1>
          <p className="muted">{formatRange(meeting.startsAt, meeting.endsAt)}</p>
        </div>
        {meeting.canEdit && !editing && (
          <div className="actions">
            <button type="button" onClick={() => setEditing(true)}>
              Chỉnh sửa
            </button>
            <button type="button" className="button-secondary" onClick={() => run(() => api(`/api/meetings/${meeting.id}/${cancelled ? 'reopen' : 'cancel'}`, { method: 'POST' }))}>
              {cancelled ? 'Mở lại' : 'Hủy cuộc họp'}
            </button>
            <button
              type="button"
              className="button-danger"
              onClick={async () => {
                if (!window.confirm('Xóa cuộc họp này cùng biên bản và action item?')) return
                try {
                  await api(`/api/meetings/${meeting.id}`, { method: 'DELETE' })
                  navigate(`/projects/${project.id}/meetings`)
                } catch (err) {
                  setActionError(errorMessage(err))
                }
              }}
            >
              Xóa
            </button>
          </div>
        )}
      </header>
      <Alert>{actionError}</Alert>

      {editing ? (
        <MeetingForm
          projectId={project.id}
          meeting={meeting}
          onSaved={() => {
            setEditing(false)
            reload()
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <div className="two-column">
          <div className="stack-lg">
            {(meeting.agenda || meeting.description) && (
              <section className="card stack">
                {meeting.description && <p className="prewrap">{meeting.description}</p>}
                {meeting.agenda && (
                  <>
                    <h2>Agenda</h2>
                    <p className="prewrap">{meeting.agenda}</p>
                  </>
                )}
              </section>
            )}
            <Minutes meeting={meeting} onChanged={reload} />
            <ActionItems meeting={meeting} projectId={project.id} canCreateTasks={project.permissions.includes('EDIT_ISSUES')} onChanged={reload} />
          </div>
          <div className="stack-lg">
            <Info meeting={meeting} />
            <Attendees meeting={meeting} onResponded={reload} />
            <RelatedDocuments projectId={project.id} targetType="MEETING" targetId={meeting.id} />
          </div>
        </div>
      )}
    </>
  )
}

function Info({ meeting }: { meeting: MeetingDetail }) {
  return (
    <section className="card stack">
      <h2>Thông tin</h2>
      <dl className="facts">
        <dt>Người tổ chức</dt>
        <dd>{meeting.organizer?.fullName ?? '—'}</dd>
        <dt>Địa điểm</dt>
        <dd>{meeting.location ?? '—'}</dd>
        <dt>Nhắc trước</dt>
        <dd>{REMINDER_OPTIONS.find((o) => o.value === String(meeting.reminderMinutes ?? ''))?.label.replace('Trước ', '') ?? (meeting.reminderMinutes != null ? `${meeting.reminderMinutes} phút` : 'Không')}</dd>
      </dl>
      {meeting.onlineLink && (
        <a className="button-link" href={meeting.onlineLink} target="_blank" rel="noopener noreferrer">
          Vào cuộc họp trực tuyến
        </a>
      )}
    </section>
  )
}

const RESPONSES: AttendeeResponse[] = ['ACCEPTED', 'TENTATIVE', 'DECLINED']

function Attendees({ meeting, onResponded }: { meeting: MeetingDetail; onResponded: () => void }) {
  const [error, setError] = useState<string | null>(null)
  async function respond(response: AttendeeResponse) {
    setError(null)
    try {
      await api(`/api/meetings/${meeting.id}/response`, { method: 'PUT', body: { response } })
      onResponded()
    } catch (err) {
      setError(errorMessage(err))
    }
  }
  return (
    <section className="card stack">
      <h2>Người tham dự</h2>
      {meeting.myResponse && (
        <div className="stack">
          <p className="muted small">Bạn được mời. Bạn sẽ tham dự chứ?</p>
          <div className="segmented-control" role="group" aria-label="Phản hồi của bạn">
            {RESPONSES.map((r) => (
              <button key={r} type="button" className={meeting.myResponse === r ? 'on' : ''} onClick={() => respond(r)}>
                {RESPONSE_LABELS[r]}
              </button>
            ))}
          </div>
        </div>
      )}
      <ul className="plain-list">
        {meeting.organizer && (
          <li className="row">
            <span className="workload-person">
              <Avatar name={meeting.organizer.fullName} url={meeting.organizer.avatarUrl} />
              {meeting.organizer.fullName}
            </span>
            <span className="badge">Người tổ chức</span>
          </li>
        )}
        {meeting.attendees.map((a) => (
          <li key={a.user?.id} className="row">
            <span className="workload-person">
              <Avatar name={a.user?.fullName ?? '?'} url={a.user?.avatarUrl} />
              {a.user?.fullName}
            </span>
            <span className={`badge response-${a.response.toLowerCase()}`}>{RESPONSE_LABELS[a.response]}</span>
          </li>
        ))}
      </ul>
      {meeting.attendees.length === 0 && <p className="muted small">Chưa mời ai khác.</p>}
      <Alert>{error}</Alert>
    </section>
  )
}

function Minutes({ meeting, onChanged }: { meeting: MeetingDetail; onChanged: () => void }) {
  const [draft, setDraft] = useState<{ text: string; base: number } | null>(null)
  const [conflict, setConflict] = useState<MeetingDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const templates = useResource<MeetingTemplate[]>('/api/meeting-templates')

  async function save() {
    if (!draft) return
    setBusy(true)
    setError(null)
    try {
      await api(`/api/meetings/${meeting.id}/minutes`, { method: 'PUT', body: { minutes: draft.text, baseRevision: draft.base } })
      setDraft(null)
      setConflict(null)
      onChanged()
    } catch (err) {
      if (err instanceof ApiError && err.code === 'MINUTES_CONFLICT') setConflict(await api<MeetingDetail>(`/api/meetings/${meeting.id}`))
      else setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const template = templates.data?.find((t) => t.type === meeting.type)
  return (
    <section className="card stack">
      <div className="section-header">
        <h2>Biên bản họp</h2>
        {meeting.canTakeNotes && !draft && (
          <button type="button" className="button-secondary button-small" onClick={() => setDraft({ text: meeting.minutes ?? '', base: meeting.minutesRevision })}>
            {meeting.minutes ? 'Sửa biên bản' : 'Ghi biên bản'}
          </button>
        )}
      </div>
      {draft ? (
        <>
          {conflict && (
            <div className="alert alert-error conflict-box" role="alert">
              <strong>{conflict.organizer ? 'Người khác' : 'Ai đó'} vừa lưu biên bản mới hơn.</strong>
              <span>Bản của bạn chưa được lưu để tránh ghi đè mất nội dung của họ.</span>
              <div className="actions">
                <button type="button" className="button-secondary button-small" onClick={() => (setDraft(null), setConflict(null), onChanged())}>
                  Lấy bản mới nhất, bỏ thay đổi của tôi
                </button>
                <button type="button" className="button-small" onClick={() => (setDraft({ ...draft, base: conflict.minutesRevision }), setConflict(null))}>
                  Giữ bản của tôi để lưu đè
                </button>
              </div>
            </div>
          )}
          <textarea
            className="editor-text"
            rows={16}
            maxLength={100000}
            value={draft.text}
            onChange={(e) => setDraft({ ...draft, text: e.target.value })}
            placeholder="Ghi quyết định, vấn đề phát sinh… (hỗ trợ Markdown)"
            aria-label="Biên bản họp"
          />
          <div className="actions">
            <button type="button" disabled={busy} onClick={save}>
              Lưu biên bản
            </button>
            <button type="button" className="button-secondary" onClick={() => (setDraft(null), setConflict(null))}>
              Hủy
            </button>
            {template && (
              <button type="button" className="button-secondary" onClick={() => setDraft({ ...draft, text: draft.text ? `${draft.text}\n\n${template.minutes}` : template.minutes })}>
                Chèn dàn ý của mẫu
              </button>
            )}
          </div>
          <Alert>{error}</Alert>
        </>
      ) : meeting.minutes ? (
        <Markdown source={meeting.minutes} />
      ) : (
        <p className="muted">Chưa có biên bản.{meeting.canTakeNotes && ' Bấm "Ghi biên bản" để bắt đầu, có thể chèn dàn ý theo mẫu cuộc họp.'}</p>
      )}
    </section>
  )
}

function ActionItems({ meeting, projectId, canCreateTasks, onChanged }: { meeting: MeetingDetail; projectId: string; canCreateTasks: boolean; onChanged: () => void }) {
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${projectId}/members`)
  const [text, setText] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function change(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const toggle = (item: ActionItem) =>
    change(() =>
      api(`/api/meeting-action-items/${item.id}`, {
        method: 'PUT',
        body: { text: item.text, assigneeId: item.assignee?.id ?? null, dueDate: item.dueDate, status: item.status === 'DONE' ? 'OPEN' : 'DONE' },
      }),
    )

  async function add(event: FormEvent) {
    event.preventDefault()
    await change(async () => {
      await api(`/api/meetings/${meeting.id}/action-items`, { method: 'POST', body: { text, assigneeId: assigneeId || null, dueDate: dueDate || null } })
      setText('')
      setAssigneeId('')
      setDueDate('')
    })
  }

  return (
    <section className="card stack">
      <h2>Action items</h2>
      {meeting.actionItems.length === 0 && <p className="muted">Chưa có việc nào được giao từ cuộc họp này.</p>}
      <ul className="plain-list">
        {meeting.actionItems.map((item) => (
          <li key={item.id} className="row action-row">
            <label className="action-check">
              <input type="checkbox" checked={item.status === 'DONE'} disabled={!meeting.canTakeNotes} onChange={() => toggle(item)} aria-label={`Hoàn thành: ${item.text}`} />
              <span className={item.status === 'DONE' ? 'struck' : ''}>{item.text}</span>
            </label>
            <div className="action-meta muted small">
              {item.assignee && <span>{item.assignee.fullName}</span>}
              {item.dueDate && <span>hạn {formatDay(item.dueDate)}</span>}
            </div>
            <div className="actions">
              {item.issue ? (
                <Link to={`/issues/${item.issue.id}`} className="badge">
                  {item.issue.key}
                </Link>
              ) : (
                meeting.canTakeNotes &&
                canCreateTasks && (
                  <button type="button" className="button-secondary button-small" onClick={() => change(() => api(`/api/meeting-action-items/${item.id}/create-task`, { method: 'POST' }))}>
                    Tạo công việc
                  </button>
                )
              )}
              {meeting.canTakeNotes && (
                <button type="button" className="button-danger button-small" onClick={() => window.confirm('Xóa action item này?') && change(() => api(`/api/meeting-action-items/${item.id}`, { method: 'DELETE' }))}>
                  Xóa
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
      {meeting.canTakeNotes && (
        <form onSubmit={add} className="inline-form">
          <label className="field action-text">
            <span>Việc cần làm</span>
            <input value={text} maxLength={500} required onChange={(e) => setText(e.target.value)} placeholder="Ví dụ: Viết test plan cho luồng thanh toán" />
          </label>
          <label className="field">
            <span>Người phụ trách</span>
            <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
              <option value="">Chưa giao</option>
              {members?.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.fullName}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Hạn</span>
            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </label>
          <button type="submit" disabled={!text.trim()}>
            Thêm
          </button>
        </form>
      )}
      <Alert>{error}</Alert>
    </section>
  )
}
