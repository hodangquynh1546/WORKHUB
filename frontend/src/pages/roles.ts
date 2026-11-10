import type { WorkspaceRole } from '../api/types'

export const ROLE_LABELS: Record<WorkspaceRole, string> = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  MEMBER: 'Thành viên',
}
