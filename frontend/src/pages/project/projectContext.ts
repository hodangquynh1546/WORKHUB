import { useOutletContext } from 'react-router'
import type { Project } from '../../api/types'

/** What ProjectLayout hands to its child routes. */
export type ProjectContext = { project: Project; reload: () => void }

export function useProject(): ProjectContext {
  return useOutletContext<ProjectContext>()
}
