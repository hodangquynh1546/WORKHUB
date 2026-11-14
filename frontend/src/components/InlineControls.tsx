import { useState } from 'react'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Issue, IssueStatus, ProjectMember } from '../api/types'
import type { useWorkflow } from '../api/useWorkflow'
import { ISSUE_PRIORITIES, PRIORITY_LABELS } from '../pages/issueLabels'

/**
 * Status, priority and assignee of an issue, editable right in a list row (no need to open the detail page).
 * Every call goes through the normal endpoints; `onDone` reloads the list, `onError` shows the message.
 */
export function InlineControls({
  issue,
  members,
  workflow,
  onDone,
  onError,
}: {
  issue: Issue
  members: ProjectMember[]
  workflow: ReturnType<typeof useWorkflow>
  onDone: () => void
  onError: (message: string) => void
}) {
  const [busy, setBusy] = useState(false)

  async function run(call: () => Promise<unknown>) {
    setBusy(true)
    try {
      await call()
      onDone()
    } catch (err) {
      onError(errorMessage(err))
      onDone()
    } finally {
      setBusy(false)
    }
  }

  const status = (s: IssueStatus) => run(() => api(`/api/issues/${issue.id}/status`, { method: 'PUT', body: { status: s } }))
  const assign = (id: string) => run(() => api(`/api/issues/${issue.id}/assignee`, { method: 'PUT', body: { assigneeId: id || null } }))
  const priority = (p: string) =>
    run(async () => {
      // the update endpoint takes the whole issue: read it first so nothing else changes
      const d = (await api<{ issue: Issue & { description?: string | null; acceptanceCriteria?: string | null } }>(`/api/issues/${issue.id}`)).issue
      await api(`/api/issues/${issue.id}`, {
        method: 'PUT',
        body: {
          type: d.type,
          title: d.title,
          description: d.description ?? null,
          priority: p,
          refinement: d.refinement,
          parentId: d.parent?.id ?? null,
          startDate: d.startDate,
          dueDate: d.dueDate,
          storyPoints: d.storyPoints,
          originalEstimateMinutes: d.originalEstimateMinutes,
          remainingEstimateMinutes: d.remainingEstimateMinutes,
          acceptanceCriteria: d.acceptanceCriteria ?? null,
          labels: d.labels,
        },
      })
    })

  return (
    <span className="inline-controls">
      <select value={issue.priority} disabled={busy} onChange={(e) => void priority(e.target.value)} aria-label="Độ ưu tiên" className={`priority-select priority-${issue.priority.toLowerCase()}`}>
        {ISSUE_PRIORITIES.map((p) => (
          <option key={p} value={p}>
            {PRIORITY_LABELS[p]}
          </option>
        ))}
      </select>
      <select value={issue.status} disabled={busy} onChange={(e) => void status(e.target.value as IssueStatus)} aria-label="Trạng thái" className={`status-select status-${issue.status.toLowerCase()}`}>
        {workflow.enabled.map((s) => (
          <option key={s} value={s}>
            {workflow.names[s]}
          </option>
        ))}
      </select>
      <select value={issue.assignee?.id ?? ''} disabled={busy} onChange={(e) => void assign(e.target.value)} aria-label="Người phụ trách" className="assignee-select">
        <option value="">Chưa giao</option>
        {members.map((m) => (
          <option key={m.userId} value={m.userId}>
            {m.fullName}
          </option>
        ))}
      </select>
    </span>
  )
}
