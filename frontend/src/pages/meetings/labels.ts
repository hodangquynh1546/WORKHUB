import type { AttendeeResponse, MeetingType } from '../../api/types'

export const MEETING_TYPE_LABELS: Record<MeetingType, string> = {
  PROJECT_KICKOFF: 'Project Kickoff',
  SPRINT_PLANNING: 'Sprint Planning',
  DAILY_SCRUM: 'Daily Scrum',
  BACKLOG_REFINEMENT: 'Backlog Refinement',
  SPRINT_REVIEW: 'Sprint Review',
  SPRINT_RETROSPECTIVE: 'Sprint Retrospective',
  GENERAL: 'Họp thông thường',
  TECHNICAL: 'Họp kỹ thuật',
}

export const MEETING_TYPES = Object.keys(MEETING_TYPE_LABELS) as MeetingType[]

export const RESPONSE_LABELS: Record<AttendeeResponse, string> = {
  INVITED: 'Chưa trả lời',
  ACCEPTED: 'Sẽ tham dự',
  TENTATIVE: 'Có thể',
  DECLINED: 'Từ chối',
}

export const REMINDER_OPTIONS = [
  { value: '', label: 'Không nhắc' },
  { value: '5', label: 'Trước 5 phút' },
  { value: '15', label: 'Trước 15 phút' },
  { value: '30', label: 'Trước 30 phút' },
  { value: '60', label: 'Trước 1 giờ' },
  { value: '1440', label: 'Trước 1 ngày' },
]

/** ISO instant → the value of a datetime-local input, in the browser's timezone. */
export function toLocalInput(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export const fromLocalInput = (value: string) => new Date(value).toISOString()

export const formatTime = (iso: string) => new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })

export const formatDayLong = (iso: string) =>
  new Date(iso).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })

/** "Thứ Hai, 10/11/2026, 09:00 – 10:00" (the end date is added when the meeting crosses midnight). */
export function formatRange(startsAt: string, endsAt: string): string {
  const sameDay = new Date(startsAt).toDateString() === new Date(endsAt).toDateString()
  return `${formatDayLong(startsAt)}, ${formatTime(startsAt)} – ${sameDay ? '' : `${formatDayLong(endsAt)}, `}${formatTime(endsAt)}`
}
