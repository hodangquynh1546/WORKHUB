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
export type WorkspacePermission = 'VIEW' | 'UPDATE_SETTINGS' | 'MANAGE_MEMBERS' | 'MANAGE_ADMINS' | 'VIEW_AUDIT' | 'MANAGE_TEAMS' | 'DELETE_WORKSPACE' | 'TRANSFER_OWNERSHIP'

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
export type TeamPermission = 'VIEW' | 'UPDATE_TEAM' | 'MANAGE_MEMBERS' | 'MANAGE_PROJECTS' | 'DELETE_TEAM'

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
  /** True when the signed-in user created the team (otherwise they were invited or manage it). */
  mine: boolean
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
  /** Set for project invitations; then `role` is a ProjectRole. */
  projectId: string | null
  projectName: string | null
  role: string
  invitedByName: string
  status: InvitationStatus
  expiresAt: string
  createdAt: string
}

export type InvitationCreated = { invitation: Invitation; inviteUrl: string }
export type InvitationAccepted = { workspaceId: string; teamId: string | null; projectId: string | null }

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
  | 'EDIT_DOCUMENTS'
  | 'MANAGE_MEETINGS'

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
  /** Every role the user holds in the project (they add up); empty when not a member. */
  myRoles: ProjectRole[]
  /** True when the signed-in user created the project (otherwise they were invited). */
  mine: boolean
  permissions: ProjectPermission[]
}

export type ProjectMember = {
  userId: string
  email: string
  fullName: string
  avatarUrl: string | null
  /** The most powerful of `roles`. */
  role: ProjectRole
  roles: ProjectRole[]
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
  blocked: boolean
  blockedReason: string | null
  /** The sprint the issue is in (open sprint, or the completed one it was finished in). */
  sprint: { id: string; name: string; status: SprintStatus } | null
  /** Where the issue came from in another system, e.g. smartspec:usecase/<project>/<use case>. */
  externalRef: string | null
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

export type SprintStatus = 'FUTURE' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
export type GoalOutcome = 'ACHIEVED' | 'PARTIALLY_ACHIEVED' | 'NOT_ACHIEVED'

export type Sprint = {
  id: string
  projectId: string
  name: string
  goal: string | null
  startDate: string
  endDate: string
  status: SprintStatus
  facilitator: UserRef | null
  capacityPoints: number | null
  /** Frozen when the sprint starts. */
  committedPoints: number | null
  committedIssues: number | null
  /** Set when the sprint completes (only DONE issues count). */
  completedPoints: number | null
  completedIssues: number | null
  goalOutcome: GoalOutcome | null
  goalNote: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
  /** The issues in the sprint right now (for a completed sprint: the finished ones). */
  issueCount: number
  scopePoints: number
  doneIssueCount: number
  donePoints: number
}

export type SprintDashboard = {
  totalIssues: number
  todoIssues: number
  inProgressIssues: number
  doneIssues: number
  blockedIssues: number
  overdueIssues: number
  scopePoints: number
  donePoints: number
  remainingPoints: number
  committedPoints: number | null
  /** Scope changes after the sprint started. */
  addedPoints: number
  addedIssues: number
  removedPoints: number
  removedIssues: number
}

export type SprintDetail = {
  sprint: Sprint
  dashboard: SprintDashboard
  issues: Issue[]
  permissions: ProjectPermission[]
}

export type SprintSnapshot = {
  date: string
  scopePoints: number
  donePoints: number
  scopeIssues: number
  todoIssues: number
  inProgressIssues: number
  doneIssues: number
}

export type SprintReport = {
  sprint: Sprint
  dashboard: SprintDashboard
  doneIssues: Issue[]
  unfinishedIssues: Issue[]
  removedIssues: Issue[]
  snapshots: SprintSnapshot[]
}

export type WorkflowEntry = { status: IssueStatus; name: string; enabled: boolean }

export type Board = {
  methodology: Methodology
  sprint: Sprint | null
  columns: WorkflowEntry[]
  issues: Issue[]
  permissions: ProjectPermission[]
}

// ---- Reports (M6) ----

export type ChartDay = {
  date: string
  /** False for days with no recorded state; the numbers are then null. */
  actual: boolean
  idealPoints: number
  idealIssues: number
  scopePoints: number | null
  donePoints: number | null
  remainingPoints: number | null
  scopeIssues: number | null
  todoIssues: number | null
  inProgressIssues: number | null
  doneIssues: number | null
  remainingIssues: number | null
}

export type SprintCharts = {
  sprintId: string
  name: string
  status: SprintStatus
  started: boolean
  startDate: string
  endDate: string
  workdays: number
  committedPoints: number | null
  committedIssues: number | null
  days: ChartDay[]
}

export type VelocitySprint = {
  sprintId: string
  name: string
  endDate: string
  committedPoints: number
  completedPoints: number
  committedIssues: number
  completedIssues: number
}

export type Velocity = { sprints: VelocitySprint[]; average: number | null }

export type FlowPoint = {
  issueId: string
  key: string
  title: string
  doneDate: string
  leadDays: number
  cycleDays: number | null
}

export type FlowSummary = {
  count: number
  avgLead: number | null
  medianLead: number | null
  p85Lead: number | null
  avgCycle: number | null
  medianCycle: number | null
  p85Cycle: number | null
}

export type LeadTime = { days: number; summary: FlowSummary; points: FlowPoint[] }

export type WorkloadRow = {
  userId: string | null
  fullName: string | null
  avatarUrl: string | null
  todoIssues: number
  inProgressIssues: number
  openPoints: number
}

export type Workload = { rows: WorkloadRow[] }

export type BugWeek = { weekStart: string; created: number; resolved: number; open: number }

export type BugTrend = { weeks: BugWeek[] }

// ---- Documents (M7) ----

export type DocumentType =
  | 'PROJECT_CHARTER'
  | 'BUSINESS_REQUIREMENTS'
  | 'SRS'
  | 'TECHNICAL_DESIGN'
  | 'API_DOCUMENTATION'
  | 'MEETING_MINUTES'
  | 'SPRINT_PLANNING_NOTES'
  | 'SPRINT_REVIEW_REPORT'
  | 'SPRINT_RETROSPECTIVE_REPORT'
  | 'TEST_PLAN'
  | 'RELEASE_NOTES'
  | 'OTHER'

export type DocumentStatus = 'ACTIVE' | 'ARCHIVED'

export type DocumentFolder = { id: string; parentId: string | null; name: string; documentCount: number }

export type DocumentSummary = {
  id: string
  title: string
  type: DocumentType
  folderId: string | null
  status: DocumentStatus
  version: number
  excerpt: string
  updatedBy: UserRef | null
  updatedAt: string
  attachmentCount: number
  /** Owned by SmartSpec: read-only here. */
  managed: boolean
}

export type DocumentAttachment = {
  id: string
  fileName: string
  contentType: string
  sizeBytes: number
  uploadedBy: UserRef | null
  createdAt: string
}

export type LinkTargetType = 'ISSUE' | 'SPRINT' | 'MEETING'

export type DocumentLink = { targetType: LinkTargetType; targetId: string; label: string; detail: string }

export type DocumentDetail = {
  id: string
  projectId: string
  title: string
  type: DocumentType
  folderId: string | null
  status: DocumentStatus
  version: number
  content: string
  createdBy: UserRef | null
  updatedBy: UserRef | null
  createdAt: string
  updatedAt: string
  attachments: DocumentAttachment[]
  links: DocumentLink[]
  canEdit: boolean
  canDelete: boolean
  externalRef: string | null
  managed: boolean
}

export type DocumentVersionSummary = {
  version: number
  title: string
  note: string | null
  author: UserRef | null
  createdAt: string
  length: number
}

export type DocumentVersionDetail = {
  version: number
  title: string
  content: string
  note: string | null
  author: UserRef | null
  createdAt: string
}

// ---- Meetings and calendar (M8) ----

export type MeetingType =
  | 'PROJECT_KICKOFF'
  | 'SPRINT_PLANNING'
  | 'DAILY_SCRUM'
  | 'BACKLOG_REFINEMENT'
  | 'SPRINT_REVIEW'
  | 'SPRINT_RETROSPECTIVE'
  | 'GENERAL'
  | 'TECHNICAL'

export type MeetingStatus = 'SCHEDULED' | 'CANCELLED'
export type AttendeeResponse = 'INVITED' | 'ACCEPTED' | 'TENTATIVE' | 'DECLINED'

export type MeetingTemplate = {
  type: MeetingType
  name: string
  description: string
  durationMinutes: number
  agenda: string
  minutes: string
}

export type MeetingSummary = {
  id: string
  title: string
  type: MeetingType
  status: MeetingStatus
  startsAt: string
  endsAt: string
  location: string | null
  onlineLink: string | null
  sprint: { id: string; name: string } | null
  organizer: UserRef | null
  attendeeCount: number
  openActionItems: number
  myResponse: AttendeeResponse | null
}

export type ActionItem = {
  id: string
  text: string
  assignee: UserRef | null
  dueDate: string | null
  status: 'OPEN' | 'DONE'
  issue: { id: string; key: string; title: string } | null
}

export type MeetingDetail = {
  id: string
  projectId: string
  title: string
  type: MeetingType
  status: MeetingStatus
  startsAt: string
  endsAt: string
  location: string | null
  onlineLink: string | null
  agenda: string | null
  description: string | null
  minutes: string | null
  minutesRevision: number
  reminderMinutes: number | null
  sprint: { id: string; name: string } | null
  organizer: UserRef | null
  createdBy: UserRef | null
  attendees: { user: UserRef | null; response: AttendeeResponse }[]
  actionItems: ActionItem[]
  myResponse: AttendeeResponse | null
  canEdit: boolean
  canTakeNotes: boolean
}

export type CalendarEvent = {
  kind: 'MEETING' | 'SPRINT' | 'DUE_DATE'
  id: string
  title: string
  startsAt: string | null
  endsAt: string | null
  startDate: string | null
  endDate: string | null
  status: string
  detail: string | null
}

// ---- Notifications, alerts, chat (M9) ----

export type NotificationType =
  | 'ISSUE_ASSIGNED'
  | 'ISSUE_STATUS_CHANGED'
  | 'ISSUE_COMMENTED'
  | 'MENTIONED'
  | 'INVITED_TO_TEAM'
  | 'INVITED_TO_PROJECT'
  | 'MEETING_INVITED'
  | 'MEETING_CANCELLED'
  | 'MEETING_REMINDER'
  | 'SPRINT_STARTED'
  | 'SPRINT_COMPLETED'
  | 'TASK_BLOCKED'
  | 'ALERT_OVERDUE'
  | 'ALERT_DUE_SOON'
  | 'ALERT_SPRINT_AT_RISK'
  | 'ALERT_SCOPE_CHANGE'
  | 'ALERT_OVERLOAD'
  | 'ALERT_NO_PROGRESS'

export type AppNotification = {
  id: string
  type: NotificationType
  alert: boolean
  title: string
  body: string | null
  link: string | null
  projectId: string | null
  actorId: string | null
  read: boolean
  createdAt: string
}

export type NotificationPage = { items: AppNotification[]; page: number; size: number; total: number; unread: number }

export type NotificationPreference = { type: NotificationType; alert: boolean; inApp: boolean; email: boolean }

export type ProjectAlert = {
  kind: NotificationType
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  title: string
  detail: string | null
  link: string | null
  subjectId: string | null
}

export type AlertSettings = {
  overdueEnabled: boolean
  dueSoonDays: number
  sprintRiskDaysLeft: number
  sprintRiskOpenPct: number
  blockedEnabled: boolean
  overloadOpenIssues: number
  scopeChangePct: number
  noProgressDays: number
}

export type ConversationKind = 'PROJECT' | 'TEAM' | 'DIRECT' | 'GROUP'

export type ConversationSummary = {
  id: string
  kind: ConversationKind
  title: string
  projectId: string | null
  teamId: string | null
  other: UserRef | null
  memberCount: number
  last: { senderId: string; senderName: string; snippet: string; createdAt: string } | null
  unread: number
  lastMessageAt: string | null
}

export type ChatMessage = {
  id: string
  conversationId: string
  sender: UserRef | null
  body: string | null
  replyTo: { id: string; senderName: string; snippet: string | null; deleted: boolean } | null
  createdAt: string
  editedAt: string | null
  deleted: boolean
  reactions: { emoji: string; userIds: string[] }[]
  mentions: string[]
  attachments: ChatAttachment[]
}

export type ChatAttachment = { id: string; fileName: string; contentType: string; size: number; image: boolean }

/** `userId` has read everything sent up to `lastReadAt`. */
export type ChatReceipt = { userId: string; lastReadAt: string }

export type ChatMessagePage = { items: ChatMessage[]; hasMore: boolean }

export type ChatSearchHit = { message: ChatMessage; conversationTitle: string }

// ---- Audit log (M10) ----

export type AuditEntry = {
  id: string
  createdAt: string
  actorId: string | null
  actorName: string | null
  action: string
  targetType: string | null
  targetId: string | null
  targetLabel: string | null
  detail: string | null
  outcome: 'SUCCESS' | 'FAILED'
  projectId: string | null
  ip: string | null
}
