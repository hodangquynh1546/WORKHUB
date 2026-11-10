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

export type Workspace = {
  id: string
  name: string
  description: string | null
  logoUrl: string | null
  createdAt: string
  myRole: WorkspaceRole
}

export type WorkspaceMember = {
  userId: string
  email: string
  fullName: string
  avatarUrl: string | null
  role: WorkspaceRole
  joinedAt: string
}
