import type { Project, Workspace } from '../api/types'
import { useResource } from '../api/useResource'
import { Alert } from '../components/Form'
import { ProjectList } from '../components/ProjectList'
import { ListSkeleton } from '../components/Skeleton'
import { useInvitationsLive } from '../realtime/useInvitationsLive'

/** Every project the signed-in user can open, across all workspaces and teams, grouped by workspace. */
export function ProjectsPage() {
  const { data: projects, error, reload } = useResource<Project[]>('/api/me/projects')
  const { data: workspaces } = useResource<Workspace[]>('/api/workspaces')
  useInvitationsLive(reload)
  const mine = (projects ?? []).filter((p) => p.mine)
  const invited = (projects ?? []).filter((p) => !p.mine)

  return (
    <section className="stack-lg">
      <Alert>{error}</Alert>
      {projects === null && !error && <ListSkeleton />}
      <ProjectGroups title="Dự án của bạn" projects={mine} workspaces={workspaces ?? []} empty="Bạn chưa tạo dự án nào." showEmpty={projects !== null} />
      {invited.length > 0 && <ProjectGroups title="Dự án được mời" projects={invited} workspaces={workspaces ?? []} />}
    </section>
  )
}

/** Projects under their workspace's name; a workspace the user is only a guest of has no name to show, so those sit together. */
function ProjectGroups({ title, projects, workspaces, empty, showEmpty }: { title: string; projects: Project[]; workspaces: Workspace[]; empty?: string; showEmpty?: boolean }) {
  const known = workspaces
    .map((w) => ({ id: w.id, name: w.name as string | null, projects: projects.filter((p) => p.workspaceId === w.id) }))
    .filter((g) => g.projects.length > 0)
  const knownIds = new Set(workspaces.map((w) => w.id))
  const others = projects.filter((p) => !knownIds.has(p.workspaceId))
  const groups = others.length > 0 ? [...known, { id: 'other', name: null, projects: others }] : known
  return (
    <div className="stack">
      <h2>{title}</h2>
      {showEmpty && projects.length === 0 && empty && <p className="muted">{empty}</p>}
      {groups.map((g) => (
        <div key={g.id} className="card stack">
          {g.name && <h3>{g.name}</h3>}
          <ProjectList projects={g.projects} showTeam />
        </div>
      ))}
    </div>
  )
}
