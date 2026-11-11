import { Link } from 'react-router'
import type { Team, Workspace } from '../api/types'
import { useResource } from '../api/useResource'
import { Alert } from '../components/Form'
import { useInvitationsLive } from '../realtime/useInvitationsLive'
import { ListSkeleton } from '../components/Skeleton'

/** Every team the signed-in user belongs to (or manages), across all workspaces, without opening each workspace first. */
export function TeamsPage() {
  const { data: teams, error, reload } = useResource<Team[]>('/api/me/teams')
  const { data: workspaces } = useResource<Workspace[]>('/api/workspaces')
  useInvitationsLive(reload)
  const invited = teams?.filter((t) => !t.mine) ?? []
  const workspaceName = (id: string) => workspaces?.find((w) => w.id === id)?.name

  return (
    <section className="stack-lg">
      <Alert>{error}</Alert>
      {teams === null && !error && <ListSkeleton />}
      <div className="card">
        <h2>Team của bạn</h2>
        {teams?.length === 0 && <p className="muted">Bạn chưa có team nào. Khi tạo hoặc được mời vào một team, nó sẽ hiện ở đây.</p>}
        <TeamRows teams={teams?.filter((t) => t.mine) ?? []} workspaceName={workspaceName} />
      </div>
      {invited.length > 0 && (
        <div className="card">
          <h2>Team được mời</h2>
          <TeamRows teams={invited} workspaceName={workspaceName} />
        </div>
      )}
    </section>
  )
}

function TeamRows({ teams, workspaceName }: { teams: Team[]; workspaceName: (id: string) => string | undefined }) {
  return (
    <ul className="plain-list">
      {teams.map((team) => (
        <li key={team.id}>
          <Link to={`/teams/${team.id}`} className="row row-link">
            <div>
              <strong>{team.name}</strong>
              <p className="muted small">
                {workspaceName(team.workspaceId) && `${workspaceName(team.workspaceId)} · `}
                {team.memberCount} thành viên{team.myRole === 'LEAD' && ' · bạn là trưởng nhóm'}
                {team.myRole === 'MEMBER' && ' · bạn là thành viên'}
              </p>
            </div>
            <span aria-hidden="true">›</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
