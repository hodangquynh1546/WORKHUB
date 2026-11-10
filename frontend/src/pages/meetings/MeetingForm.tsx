import { useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { MeetingDetail, MeetingTemplate, MeetingType, ProjectMember, Sprint } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Field } from '../../components/Form'
import { MEETING_TYPES, MEETING_TYPE_LABELS, REMINDER_OPTIONS, fromLocalInput, toLocalInput } from './labels'

type Form = {
  type: MeetingType
  title: string
  start: string
  end: string
  sprintId: string
  organizerId: string
  attendeeIds: string[]
  location: string
  onlineLink: string
  agenda: string
  description: string
  reminder: string
}

/** Next full hour in the browser's timezone, as a datetime-local value. */
function nextHour(): string {
  const d = new Date()
  d.setMinutes(0, 0, 0)
  d.setHours(d.getHours() + 1)
  return toLocalInput(d.toISOString())
}

const addMinutes = (local: string, minutes: number) => toLocalInput(new Date(new Date(local).getTime() + minutes * 60_000).toISOString())

/** Creates a meeting (the template picks type, length and agenda) or edits an existing one. */
export function MeetingForm({
  projectId,
  meeting,
  onSaved,
  onCancel,
}: {
  projectId: string
  meeting?: MeetingDetail
  onSaved: (meeting: MeetingDetail) => void
  onCancel: () => void
}) {
  const { user } = useAuth()
  const templates = useResource<MeetingTemplate[]>('/api/meeting-templates')
  const members = useResource<ProjectMember[]>(`/api/projects/${projectId}/members`)
  const sprints = useResource<Sprint[]>(`/api/projects/${projectId}/sprints`)
  const [form, setForm] = useState<Form>(() =>
    meeting
      ? {
          type: meeting.type,
          title: meeting.title,
          start: toLocalInput(meeting.startsAt),
          end: toLocalInput(meeting.endsAt),
          sprintId: meeting.sprint?.id ?? '',
          organizerId: meeting.organizer?.id ?? '',
          attendeeIds: meeting.attendees.flatMap((a) => (a.user ? [a.user.id] : [])),
          location: meeting.location ?? '',
          onlineLink: meeting.onlineLink ?? '',
          agenda: meeting.agenda ?? '',
          description: meeting.description ?? '',
          reminder: meeting.reminderMinutes?.toString() ?? '',
        }
      : { type: 'GENERAL', title: '', start: nextHour(), end: addMinutes(nextHour(), 30), sprintId: '', organizerId: '', attendeeIds: [], location: '', onlineLink: '', agenda: '', description: '', reminder: '15' },
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (patch: Partial<Form>) => setForm((f) => ({ ...f, ...patch }))

  function applyTemplate(type: MeetingType) {
    const template = templates.data?.find((t) => t.type === type)
    if (!template || meeting) {
      set({ type })
      return
    }
    set({
      type,
      agenda: template.agenda,
      end: addMinutes(form.start, template.durationMinutes),
      title: form.title.trim() ? form.title : template.name,
    })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const body = {
      title: form.title,
      type: form.type,
      sprintId: form.sprintId || null,
      organizerId: form.organizerId || null,
      startsAt: fromLocalInput(form.start),
      endsAt: fromLocalInput(form.end),
      location: form.location || null,
      onlineLink: form.onlineLink || null,
      agenda: form.agenda || null,
      description: form.description || null,
      reminderMinutes: form.reminder ? Number(form.reminder) : null,
      attendeeIds: form.attendeeIds,
    }
    try {
      onSaved(
        meeting
          ? await api<MeetingDetail>(`/api/meetings/${meeting.id}`, { method: 'PUT', body })
          : await api<MeetingDetail>(`/api/projects/${projectId}/meetings`, { method: 'POST', body }),
      )
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  const people = members.data ?? []
  const organizer = form.organizerId || meeting?.organizer?.id || user?.id
  return (
    <form onSubmit={submit} className="card stack meeting-form">
      <h2>{meeting ? 'Sửa cuộc họp' : 'Lên lịch cuộc họp'}</h2>
      {!meeting && (
        <div className="template-grid" role="group" aria-label="Chọn mẫu cuộc họp">
          {templates.data?.map((t) => (
            <button key={t.type} type="button" className={`template-card ${form.type === t.type ? 'on' : ''}`} onClick={() => applyTemplate(t.type)}>
              <strong>{t.name}</strong>
              <span>{t.description}</span>
              <span className="small">{t.durationMinutes} phút</span>
            </button>
          ))}
        </div>
      )}
      {meeting && (
        <label className="field">
          <span>Loại</span>
          <select value={form.type} onChange={(e) => set({ type: e.target.value as MeetingType })}>
            {MEETING_TYPES.map((t) => (
              <option key={t} value={t}>
                {MEETING_TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
      )}
      <Field label="Tiêu đề" required maxLength={200} value={form.title} onChange={(e) => set({ title: e.target.value })} autoFocus />
      <div className="inline-form">
        <label className="field">
          <span>Bắt đầu</span>
          <input
            type="datetime-local"
            required
            value={form.start}
            onChange={(e) => {
              // Keep the length when the start moves.
              const length = new Date(form.end).getTime() - new Date(form.start).getTime()
              set({ start: e.target.value, end: length > 0 ? addMinutes(e.target.value, length / 60_000) : form.end })
            }}
          />
        </label>
        <label className="field">
          <span>Kết thúc</span>
          <input type="datetime-local" required value={form.end} min={form.start} onChange={(e) => set({ end: e.target.value })} />
        </label>
        <label className="field">
          <span>Nhắc trước giờ họp</span>
          <select value={form.reminder} onChange={(e) => set({ reminder: e.target.value })}>
            {REMINDER_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="inline-form">
        <Field label="Địa điểm" maxLength={255} value={form.location} onChange={(e) => set({ location: e.target.value })} />
        <Field label="Liên kết họp trực tuyến" type="url" maxLength={500} placeholder="https://…" value={form.onlineLink} onChange={(e) => set({ onlineLink: e.target.value })} />
      </div>
      <div className="inline-form">
        <label className="field">
          <span>Người tổ chức</span>
          <select value={form.organizerId} onChange={(e) => set({ organizerId: e.target.value })}>
            <option value="">{meeting ? 'Giữ nguyên' : 'Tôi'}</option>
            {people.map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.fullName}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Sprint liên quan</span>
          <select value={form.sprintId} onChange={(e) => set({ sprintId: e.target.value })}>
            <option value="">Không</option>
            {sprints.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <fieldset className="radio-group attendee-picker">
        <legend className="muted small">Người tham dự</legend>
        <div className="attendee-grid">
          {people
            .filter((m) => m.userId !== organizer)
            .map((m) => (
              <label key={m.userId} className="inline-check attendee-option">
                <input
                  type="checkbox"
                  checked={form.attendeeIds.includes(m.userId)}
                  onChange={(e) => set({ attendeeIds: e.target.checked ? [...form.attendeeIds, m.userId] : form.attendeeIds.filter((id) => id !== m.userId) })}
                />
                {m.fullName}
              </label>
            ))}
        </div>
      </fieldset>
      <label className="field">
        <span>Agenda</span>
        <textarea rows={6} maxLength={20000} value={form.agenda} onChange={(e) => set({ agenda: e.target.value })} />
      </label>
      <label className="field">
        <span>Mô tả (không bắt buộc)</span>
        <textarea rows={3} maxLength={5000} value={form.description} onChange={(e) => set({ description: e.target.value })} />
      </label>
      <Alert>{error}</Alert>
      <div className="actions">
        <button type="submit" disabled={busy || !form.title.trim()}>
          {meeting ? 'Lưu thay đổi' : 'Lên lịch'}
        </button>
        <button type="button" className="button-secondary" onClick={onCancel}>
          Hủy
        </button>
      </div>
    </form>
  )
}
