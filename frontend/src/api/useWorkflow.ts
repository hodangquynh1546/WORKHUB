import { useMemo } from 'react'
import type { IssueStatus, WorkflowEntry } from './types'
import { useResource } from './useResource'
import { STATUS_LABELS } from '../pages/issueLabels'

/**
 * The project's own names for the five statuses and which of them it uses. Until it loads, the default names are
 * returned so screens do not flicker.
 */
export function useWorkflow(projectId: string) {
  const { data, reload } = useResource<WorkflowEntry[]>(`/api/projects/${projectId}/workflow`)
  return useMemo(() => {
    const names = { ...STATUS_LABELS } as Record<IssueStatus, string>
    data?.forEach((entry) => (names[entry.status] = entry.name))
    const enabled = (data ?? []).filter((entry) => entry.enabled).map((entry) => entry.status)
    return { names, enabled: enabled.length ? enabled : (Object.keys(STATUS_LABELS) as IssueStatus[]), entries: data, reload }
  }, [data, reload])
}
