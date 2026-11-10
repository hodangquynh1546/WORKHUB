export type UserProfile = {
  id: string
  email: string
  fullName: string
  avatarUrl: string | null
  emailVerified: boolean
  createdAt: string
}

export type AuthResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserProfile
}

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'MEMBER'
export type WorkspacePermission = 'VIEW' | 'UPDATE_SETTINGS' | 'MANAGE_MEMBERS' | 'MANAGE_ADMINS' | 'MANAGE_TEAMS'

export type Workspace = {
  id: string
  name: string
  description: string | null
  logoUrl: string | null
  createdAt: string
  myRole: WorkspaceRole
  /** What the signed-in user may do here. The backend enforces it; the UI only hides what would fail. */
  permissions: WorkspacePermission[]
}

export type WorkspaceMember = {
  userId: string
  email: string
  fullName: string
  avatarUrl: string | null
  role: WorkspaceRole
  joinedAt: string
}

export type TeamRole = 'LEAD' | 'MEMBER'
export type TeamPermission = 'VIEW' | 'UPDATE_TEAM' | 'MANAGE_MEMBERS'

export type Team = {
  id: string
  workspaceId: string
  name: string
  description: string | null
  archived: boolean
  createdAt: string
  memberCount: number
  /** Null when the signed-in user is not on the team. */
  myRole: TeamRole | null
  permissions: TeamPermission[]
}

export type TeamMember = {
  userId: string
  email: string
  fullName: string
  avatarUrl: string | null
  role: TeamRole
  joinedAt: string
}

export type InvitationKind = 'EMAIL' | 'LINK'
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED'

export type Invitation = {
  id: string
  kind: InvitationKind
  email: string | null
  workspaceId: string
  workspaceName: string
  /** Set for team invitations; then `role` is a TeamRole, otherwise a WorkspaceRole. */
  teamId: string | null
  teamName: string | null
  role: string
  invitedByName: string
  status: InvitationStatus
  expiresAt: string
  createdAt: string
}

export type InvitationCreated = { invitation: Invitation; inviteUrl: string }
export type InvitationAccepted = { workspaceId: string; teamId: string | null }
