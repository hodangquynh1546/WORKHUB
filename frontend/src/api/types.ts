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
export type TeamPermission = 'VIEW' | 'UPDATE_TEAM' | 'MANAGE_MEMBERS' | 'MANAGE_PROJECTS'

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

export type Methodology = 'SCRUM' | 'KANBAN'
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED'
export type ProjectRole = 'PROJECT_MANAGER' | 'PRODUCT_OWNER' | 'SCRUM_MASTER' | 'DEVELOPER' | 'QA_ENGINEER' | 'STAKEHOLDER'
export type ProjectPermission =
  | 'VIEW'
  | 'UPDATE_PROJECT'
  | 'MANAGE_MEMBERS'
  | 'MANAGE_BACKLOG'
  | 'MANAGE_SPRINTS'
  | 'EDIT_ISSUES'
  | 'COMMENT'

export type Project = {
  id: string
  workspaceId: string
  teamId: string
  teamName: string
  /** Short code such as ECOM; fixed after creation. */
  key: string
  name: string
  description: string | null
  avatarUrl: string | null
  /** ISO dates (yyyy-mm-dd) or null. */
  startDate: string | null
  endDate: string | null
  methodology: Methodology
  status: ProjectStatus
  createdAt: string
  memberCount: number
  /** Null when the signed-in user is not a project member (e.g. sees it through the team). */
  myRole: ProjectRole | null
  permissions: ProjectPermission[]
}

export type ProjectMember = {
  userId: string
  email: string
  fullName: string
  avatarUrl: string | null
  role: ProjectRole
  joinedAt: string
}

export type IssueType = 'EPIC' | 'FEATURE' | 'USER_STORY' | 'TASK' | 'SUB_TASK' | 'BUG' | 'SPIKE'
export type IssueStatus = 'TO_DO' | 'IN_PROGRESS' | 'CODE_REVIEW' | 'TESTING' | 'DONE'
export type IssuePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type RefinementStatus = 'NEW' | 'REFINING' | 'READY'

export type UserRef = { id: string; fullName: string; avatarUrl: string | null }
export type IssueRef = { id: string; key: string; title: string; type: IssueType }

export type Issue = {
  id: string
  projectId: string
  /** Such as ECOM-12. */
  key: string
  number: number
  type: IssueType
  title: string
  /** Only present in detail views. */
  description: string | null
  priority: IssuePriority
  status: IssueStatus
  refinement: RefinementStatus
  reporter: UserRef
  assignee: UserRef | null
  parent: IssueRef | null
  /** Nearest Epic above this issue, at any depth. */
  epic: IssueRef | null
  startDate: string | null
  dueDate: string | null
  storyPoints: number | null
  originalEstimateMinutes: number | null
  remainingEstimateMinutes: number | null
  /** Only present in detail views. */
  acceptanceCriteria: string | null
  labels: string[]
  rank: number
  startedAt: string | null
  doneAt: string | null
  createdAt: string
  updatedAt: string
  childCount: number
  childDoneCount: number
}

export type ChecklistItem = { id: string; text: string; done: boolean }

export type IssueDetail = {
  issue: Issue
  children: Issue[]
  checklist: ChecklistItem[]
  permissions: ProjectPermission[]
}

export type IssueComment = { id: string; author: UserRef; body: string; createdAt: string; updatedAt: string }

export type IssueActivity = {
  id: string
  actor: UserRef
  kind: 'CREATED' | 'CHANGED'
  field: string | null
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

export type ProjectStats = {
  total: number
  toDo: number
  inProgress: number
  done: number
  overdue: number
  totalPoints: number
  donePoints: number
}

export type Page<T> = { items: T[]; page: number; size: number; total: number; totalPages: number }
