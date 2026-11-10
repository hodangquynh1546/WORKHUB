import { useMemo, useState } from 'react'
import { Link } from 'react-router'
import type { CalendarEvent, ProjectMember } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'
import { useProject } from '../project/projectContext'
import { MEETING_TYPE_LABELS, formatDayLong, formatTime } from './labels'
import type { MeetingType } from '../../api/types'

const pad = (n: number) => String(n).padStart(2, '0')
/** yyyy-mm-dd of a Date in the browser's timezone. */
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n)
const WEEKDAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']

/** The 6x7 grid of days (Monday first) that shows `month`. */
function gridDays(month: Date): Date[] {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = addDays(first, -offset)
  return Array.from({ length: 42 }, (_, i) => addDays(start, i))
}

type Layer = 'MEETING' | 'SPRINT' | 'DUE_DATE'
const LAYERS: { id: Layer; label: string }[] = [
  { id: 'MEETING', label: 'Cuộc họp' },
  { id: 'SPRINT', label: 'Sprint' },
  { id: 'DUE_DATE', label: 'Hạn công việc' },
]

export function CalendarPage() {
  const { project } = useProject()
  const [today] = useState(() => new Date())
  const [month, setMonth] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1))
  const [selected, setSelected] = useState(() => iso(today))
  const [personId, setPersonId] = useState('')
  const [layers, setLayers] = useState<Set<Layer>>(new Set(['MEETING', 'SPRINT', 'DUE_DATE']))
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${project.id}/members`)

  const days = useMemo(() => gridDays(month), [month])
  // One day of slack on both sides: the server cuts days in its own timezone, the browser in the viewer's.
  const from = iso(addDays(days[0], -1))
  const to = iso(addDays(days[41], 1))
  const { data, error } = useResource<{ events: CalendarEvent[] }>(`/api/projects/${project.id}/calendar?from=${from}&to=${to}${personId ? `&personId=${personId}` : ''}`)

  const byDay = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>()
    const push = (key: string, event: CalendarEvent) => map.set(key, [...(map.get(key) ?? []), event])
    for (const e of data?.events ?? []) {
      if (!layers.has(e.kind)) continue
      if (e.kind === 'MEETING' && e.startsAt) push(iso(new Date(e.startsAt)), e)
      else if (e.kind === 'DUE_DATE' && e.startDate) push(e.startDate, e)
      else if (e.kind === 'SPRINT' && e.startDate && e.endDate) {
        for (let d = new Date(`${e.startDate}T00:00:00`); iso(d) <= e.endDate; d = addDays(d, 1)) push(iso(d), e)
      }
    }
    for (const list of map.values()) list.sort((a, b) => order(a) - order(b) || (a.startsAt ?? '').localeCompare(b.startsAt ?? ''))
    return map
  }, [data, layers])

  const selectedEvents = byDay.get(selected) ?? []
  const todayIso = iso(today)
  const title = month.toLocaleDateString('vi-VN', { month: 'long', year: 'numeric' })

  return (
    <div className="stack-lg">
      <div className="filters calendar-bar">
        <div className="calendar-nav">
          <button type="button" className="button-secondary button-small" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))} aria-label="Tháng trước">
            ‹
          </button>
          <h2 className="calendar-title">{title}</h2>
          <button type="button" className="button-secondary button-small" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} aria-label="Tháng sau">
            ›
          </button>
          <button type="button" className="button-secondary button-small" onClick={() => (setMonth(new Date(today.getFullYear(), today.getMonth(), 1)), setSelected(todayIso))}>
            Hôm nay
          </button>
        </div>
        <select value={personId} onChange={(e) => setPersonId(e.target.value)} aria-label="Lọc theo thành viên">
          <option value="">Cả dự án</option>
          {members?.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.fullName}
            </option>
          ))}
        </select>
        {LAYERS.map((l) => (
          <label key={l.id} className="inline-check">
            <input
              type="checkbox"
              checked={layers.has(l.id)}
              onChange={(e) => {
                const next = new Set(layers)
                if (e.target.checked) next.add(l.id)
                else next.delete(l.id)
                setLayers(next)
              }}
            />{' '}
            {l.label}
          </label>
        ))}
      </div>
      <Alert>{error}</Alert>

      <div className="calendar card" role="grid" aria-label={title}>
        {WEEKDAYS.map((d) => (
          <div key={d} className="calendar-weekday" role="columnheader">
            {d}
          </div>
        ))}
        {days.map((day) => {
          const key = iso(day)
          const events = byDay.get(key) ?? []
          const sprints = events.filter((e) => e.kind === 'SPRINT')
          const others = events.filter((e) => e.kind !== 'SPRINT')
          return (
            <button
              key={key}
              type="button"
              role="gridcell"
              className={`calendar-day ${day.getMonth() !== month.getMonth() ? 'other' : ''} ${key === selected ? 'selected' : ''} ${key === todayIso ? 'today' : ''}`}
              onClick={() => setSelected(key)}
              aria-label={`${day.toLocaleDateString('vi-VN', { dateStyle: 'full' })}, ${events.length} sự kiện`}
            >
              <span className="calendar-date">{day.getDate()}</span>
              {sprints.map((s) => (
                <span key={s.id} className={`cal-sprint ${key === s.startDate ? 'first' : ''} ${key === s.endDate ? 'last' : ''}`}>
                  {key === s.startDate || day.getDay() === 1 ? s.title : ' '}
                </span>
              ))}
              {others.slice(0, 3).map((e) => (
                <span key={`${e.kind}-${e.id}`} className={`cal-event cal-${e.kind.toLowerCase()} ${e.status === 'CANCELLED' || e.status === 'DONE' ? 'dim' : ''}`}>
                  {e.kind === 'MEETING' && e.startsAt ? `${formatTime(e.startsAt)} ` : ''}
                  {e.title}
                </span>
              ))}
              {others.length > 3 && <span className="cal-more">+{others.length - 3} nữa</span>}
              {others.length > 0 && <span className="cal-dots" aria-hidden="true">{'•'.repeat(Math.min(3, others.length))}</span>}
            </button>
          )
        })}
      </div>

      <section className="card stack">
        <h2>{formatDayLong(`${selected}T12:00:00`)}</h2>
        {selectedEvents.length === 0 && <p className="muted">Không có sự kiện nào trong ngày này.</p>}
        <ul className="plain-list">
          {selectedEvents.map((e) => (
            <li key={`${e.kind}-${e.id}`} className="row">
              <div>
                <EventLink event={e} projectId={project.id} />
                <p className="muted small">{describe(e)}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

const order = (e: CalendarEvent) => (e.kind === 'SPRINT' ? 0 : e.kind === 'MEETING' ? 1 : 2)

function EventLink({ event, projectId }: { event: CalendarEvent; projectId: string }) {
  const to = event.kind === 'MEETING' ? `/projects/${projectId}/meetings/${event.id}` : event.kind === 'SPRINT' ? `/projects/${projectId}/sprints/${event.id}` : `/issues/${event.id}`
  return <Link to={to}>{event.title}</Link>
}

function describe(e: CalendarEvent): string {
  if (e.kind === 'MEETING' && e.startsAt && e.endsAt) {
    const type = MEETING_TYPE_LABELS[(e.detail ?? 'GENERAL') as MeetingType]
    return `${formatTime(e.startsAt)} – ${formatTime(e.endsAt)} · ${type}${e.status === 'CANCELLED' ? ' · đã hủy' : ''}`
  }
  if (e.kind === 'SPRINT') return `Sprint ${e.startDate?.split('-').reverse().join('/')} → ${e.endDate?.split('-').reverse().join('/')}`
  return e.status === 'DONE' ? 'Hạn công việc · đã hoàn thành' : 'Hạn công việc'
}
