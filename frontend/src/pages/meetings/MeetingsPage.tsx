import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type { MeetingSummary, MeetingType } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useAuth } from '../../auth/AuthContext'
import { Alert } from '../../components/Form'
import { ListSkeleton } from '../../components/Skeleton'
import { useProject } from '../project/projectContext'
import { MeetingForm } from './MeetingForm'
import { MEETING_TYPES, MEETING_TYPE_LABELS, RESPONSE_LABELS, formatDayLong, formatTime } from './labels'

export function MeetingsPage() {
  const { project } = useProject()
  const { user } = useAuth()
  const navigate = useNavigate()
  const canManage = project.permissions.includes('MANAGE_MEETINGS') && project.status !== 'ARCHIVED'
  const [when, setWhen] = useState<'upcoming' | 'past'>('upcoming')
  const [type, setType] = useState<MeetingType | ''>('')
  const [mine, setMine] = useState(false)
  const [creating, setCreating] = useState(false)
  // The split between "upcoming" and "past" is fixed when the page opens, so the list does not refetch every render.
  const [now] = useState(() => new Date().toISOString())

  const query = new URLSearchParams()
  if (when === 'upcoming') query.set('from', now)
  else {
    query.set('to', now)
    query.set('newestFirst', 'true')
  }
  if (type) query.set('type', type)
  if (mine && user) query.set('personId', user.id)
  const { data, error } = useResource<MeetingSummary[]>(`/api/projects/${project.id}/meetings?${query}`)

  // Group by calendar day (browser timezone), keeping the server's order.
  const groups = useMemo(() => {
    const out: { day: string; items: MeetingSummary[] }[] = []
    for (const m of data ?? []) {
      const day = new Date(m.startsAt).toDateString()
      const last = out.at(-1)
      if (last?.day === day) last.items.push(m)
      else out.push({ day, items: [m] })
    }
    return out
  }, [data])

  return (
    <div className="stack-lg">
      <div className="filters">
        <div className="segmented-control" role="group" aria-label="Thời gian">
          <button type="button" className={when === 'upcoming' ? 'on' : ''} onClick={() => setWhen('upcoming')}>
            Sắp tới
          </button>
          <button type="button" className={when === 'past' ? 'on' : ''} onClick={() => setWhen('past')}>
            Đã qua
          </button>
        </div>
        <select value={type} onChange={(e) => setType(e.target.value as MeetingType | '')} aria-label="Loại cuộc họp">
          <option value="">Mọi loại</option>
          {MEETING_TYPES.map((t) => (
            <option key={t} value={t}>
              {MEETING_TYPE_LABELS[t]}
            </option>
          ))}
        </select>
        <label className="inline-check">
          <input type="checkbox" checked={mine} onChange={(e) => setMine(e.target.checked)} /> Của tôi
        </label>
        {canManage && (
          <button type="button" className="docs-new" onClick={() => setCreating((v) => !v)}>
            Lên lịch họp
          </button>
        )}
      </div>

      {creating && <MeetingForm projectId={project.id} onSaved={(m) => navigate(m.id)} onCancel={() => setCreating(false)} />}

      <Alert>{error}</Alert>
      {!data && !error && <ListSkeleton />}
      {data && data.length === 0 && (
        <section className="card empty-state">
          <h2>{when === 'upcoming' ? 'Chưa có cuộc họp nào sắp tới' : 'Chưa có cuộc họp nào đã qua'}</h2>
          <p className="muted">
            {canManage && when === 'upcoming'
              ? 'Lên lịch Sprint Planning, Daily Scrum hay Retrospective từ các mẫu có sẵn.'
              : 'Khi có cuộc họp phù hợp bộ lọc, nó sẽ xuất hiện ở đây.'}
          </p>
        </section>
      )}
      {groups.map((group) => (
        <section key={group.day} className="stack">
          <h2 className="day-heading">{formatDayLong(group.items[0].startsAt)}</h2>
          <ul className="plain-list doc-list">
            {group.items.map((m) => (
              <li key={m.id}>
                <Link to={m.id} className={`card meeting-card ${m.status === 'CANCELLED' ? 'is-cancelled' : ''}`}>
                  <div className="meeting-time">
                    <strong>{formatTime(m.startsAt)}</strong>
                    <span className="muted small">{formatTime(m.endsAt)}</span>
                  </div>
                  <div className="doc-card-main">
                    <div className="doc-card-title">
                      <strong>{m.title}</strong>
                      <span className="badge">{MEETING_TYPE_LABELS[m.type]}</span>
                      {m.status === 'CANCELLED' && <span className="badge badge-muted">Đã hủy</span>}
                      {m.sprint && <span className="sprint-chip">{m.sprint.name}</span>}
                    </div>
                    <p className="muted small">
                      {m.organizer?.fullName ?? 'Ai đó'} tổ chức · {m.attendeeCount + 1} người
                      {m.location && ` · ${m.location}`}
                      {m.onlineLink && ' · trực tuyến'}
                      {m.openActionItems > 0 && ` · ${m.openActionItems} việc cần làm`}
                    </p>
                  </div>
                  {m.myResponse && <span className={`badge response-${m.myResponse.toLowerCase()}`}>{RESPONSE_LABELS[m.myResponse]}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}
