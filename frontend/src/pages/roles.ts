import type { InvitationStatus, Methodology, ProjectRole, ProjectStatus, TeamRole, WorkspaceRole } from '../api/types'

export const ROLE_LABELS: Record<WorkspaceRole, string> = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  MEMBER: 'Thành viên',
}

export const TEAM_ROLE_LABELS: Record<TeamRole, string> = {
  LEAD: 'Trưởng nhóm',
  MEMBER: 'Thành viên',
}

export const INVITATION_STATUS_TEXT: Record<InvitationStatus, string> = {
  PENDING: 'Đang chờ',
  ACCEPTED: 'Lời mời này đã được sử dụng.',
  DECLINED: 'Lời mời này đã bị từ chối.',
  REVOKED: 'Lời mời này đã bị thu hồi.',
  EXPIRED: 'Lời mời này đã hết hạn.',
}

/** Label for an invitation's role, which is a team role for team invitations. */
export function invitationRoleLabel(teamId: string | null, role: string): string {
  return teamId ? (TEAM_ROLE_LABELS[role as TeamRole] ?? role) : (ROLE_LABELS[role as WorkspaceRole] ?? role)
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('vi-VN')
}

export const PROJECT_ROLE_LABELS: Record<ProjectRole, string> = {
  PROJECT_MANAGER: 'Project Manager',
  PRODUCT_OWNER: 'Product Owner',
  SCRUM_MASTER: 'Scrum Master',
  DEVELOPER: 'Developer',
  QA_ENGINEER: 'QA Engineer',
  STAKEHOLDER: 'Stakeholder',
}

export const PROJECT_ROLES = Object.keys(PROJECT_ROLE_LABELS) as ProjectRole[]

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  PLANNING: 'Lên kế hoạch',
  ACTIVE: 'Đang thực hiện',
  ON_HOLD: 'Tạm dừng',
  COMPLETED: 'Hoàn thành',
  ARCHIVED: 'Đã lưu trữ',
}

export const PROJECT_STATUSES = Object.keys(PROJECT_STATUS_LABELS) as ProjectStatus[]

export const METHODOLOGY_LABELS: Record<Methodology, string> = {
  SCRUM: 'Scrum',
  KANBAN: 'Kanban',
}

/** Dates arrive as yyyy-mm-dd; show them as dd/mm/yyyy without timezone shifts. */
export function formatDay(day: string | null): string {
  if (!day) return '—'
  const [y, m, d] = day.split('-')
  return `${d}/${m}/${y}`
}

/**
 * How far through the planned schedule we are, by calendar days in the viewer's timezone.
 * Null when either date is missing.
 */
export function scheduleProgress(startDate: string | null, endDate: string | null, today = new Date()) {
  if (!startDate || !endDate) return null
  const day = (iso: string) => new Date(`${iso}T00:00:00`).getTime()
  const start = day(startDate)
  const end = day(endDate)
  const now = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  const DAY = 86_400_000
  const totalDays = Math.round((end - start) / DAY) + 1
  const elapsedDays = Math.min(Math.max(Math.round((now - start) / DAY) + 1, 0), totalDays)
  return {
    totalDays,
    elapsedDays,
    remainingDays: totalDays - elapsedDays,
    percent: Math.round((elapsedDays / totalDays) * 100),
    notStarted: now < start,
    finished: now > end,
  }
}

/** Today's date in the browser's timezone as yyyy-mm-dd. */
export function localIsoDate(offsetDays = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
