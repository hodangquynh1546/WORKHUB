import type { IssuePriority, IssueStatus, IssueType, RefinementStatus } from '../api/types'

export const TYPE_LABELS: Record<IssueType, string> = {
  EPIC: 'Epic',
  FEATURE: 'Feature',
  USER_STORY: 'User Story',
  TASK: 'Task',
  SUB_TASK: 'Sub-task',
  BUG: 'Bug',
  SPIKE: 'Spike',
}

export const ISSUE_TYPES = Object.keys(TYPE_LABELS) as IssueType[]

/** Mirrors the backend's hierarchy rules (IssueType.allowedParents); the backend remains the authority. */
export const ALLOWED_PARENTS: Record<IssueType, IssueType[]> = {
  EPIC: [],
  FEATURE: ['EPIC'],
  USER_STORY: ['EPIC', 'FEATURE'],
  TASK: ['EPIC', 'FEATURE'],
  BUG: ['EPIC', 'FEATURE'],
  SPIKE: ['EPIC', 'FEATURE'],
  SUB_TASK: ['USER_STORY', 'TASK', 'BUG', 'SPIKE'],
}

export const STATUS_LABELS: Record<IssueStatus, string> = {
  TO_DO: 'Cần làm',
  IN_PROGRESS: 'Đang làm',
  CODE_REVIEW: 'Code review',
  TESTING: 'Kiểm thử',
  DONE: 'Hoàn thành',
}

export const ISSUE_STATUSES = Object.keys(STATUS_LABELS) as IssueStatus[]

export const PRIORITY_LABELS: Record<IssuePriority, string> = {
  LOW: 'Thấp',
  MEDIUM: 'Trung bình',
  HIGH: 'Cao',
  CRITICAL: 'Khẩn cấp',
}

export const ISSUE_PRIORITIES = Object.keys(PRIORITY_LABELS) as IssuePriority[]

export const REFINEMENT_LABELS: Record<RefinementStatus, string> = {
  NEW: 'Mới',
  REFINING: 'Đang làm rõ',
  READY: 'Sẵn sàng',
}

export const REFINEMENTS = Object.keys(REFINEMENT_LABELS) as RefinementStatus[]

/** 90 → "1h 30m"; story points are not time and are shown separately. */
export function formatMinutes(minutes: number | null): string {
  if (minutes === null) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return [h ? `${h}h` : '', m ? `${m}m` : ''].filter(Boolean).join(' ') || '0m'
}

export function hoursToMinutes(hours: string): number | null {
  const value = parseFloat(hours.replace(',', '.'))
  return Number.isFinite(value) && value >= 0 ? Math.round(value * 60) : null
}

export function minutesToHours(minutes: number | null): string {
  return minutes === null ? '' : String(Math.round((minutes / 60) * 100) / 100)
}

/** Name shown for a history field. */
export const FIELD_LABELS: Record<string, string> = {
  title: 'tiêu đề',
  description: 'mô tả',
  type: 'loại',
  priority: 'độ ưu tiên',
  refinement: 'trạng thái làm rõ',
  parent: 'công việc cha',
  status: 'trạng thái',
  assignee: 'người phụ trách',
  startDate: 'ngày bắt đầu',
  dueDate: 'hạn hoàn thành',
  storyPoints: 'Story Points',
  originalEstimate: 'ước lượng ban đầu (phút)',
  remainingEstimate: 'ước lượng còn lại (phút)',
  acceptanceCriteria: 'tiêu chí chấp nhận',
  labels: 'nhãn',
}

/** Translates enum-valued history entries and leaves everything else as is. */
export function historyValue(field: string | null, value: string | null): string {
  if (value === null || value === '') return '—'
  if (field === 'status') return STATUS_LABELS[value as IssueStatus] ?? value
  if (field === 'priority') return PRIORITY_LABELS[value as IssuePriority] ?? value
  if (field === 'type') return TYPE_LABELS[value as IssueType] ?? value
  if (field === 'refinement') return REFINEMENT_LABELS[value as RefinementStatus] ?? value
  return value
}
