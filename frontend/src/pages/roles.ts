import type { InvitationStatus, TeamRole, WorkspaceRole } from '../api/types'

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
