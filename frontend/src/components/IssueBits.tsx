import { Avatar } from './Avatar'
import type { Issue, IssuePriority, IssueType } from '../api/types'
import { PRIORITY_LABELS, TYPE_LABELS } from '../pages/issueLabels'

export function TypeBadge({ type }: { type: IssueType }) {
  return <span className={`type-badge type-${type.toLowerCase()}`}>{TYPE_LABELS[type]}</span>
}

export function PriorityBadge({ priority }: { priority: IssuePriority }) {
  return <span className={`priority priority-${priority.toLowerCase()}`}>{PRIORITY_LABELS[priority]}</span>
}

/** Small round avatar of the assignee, or a dashed placeholder. */
export function Assignee({ user }: { user: Issue['assignee'] }) {
  if (!user) return <span className="avatar avatar-empty" title="Chưa giao" />
  return <Avatar name={user.fullName} url={user.avatarUrl} />
}
