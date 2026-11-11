import { Link } from 'react-router'
import type { Project } from '../api/types'
import { METHODOLOGY_LABELS, PROJECT_ROLE_LABELS, PROJECT_STATUS_LABELS } from '../pages/roles'

/** Projects as clickable rows. `showTeam` adds the owning team (useful in the workspace-wide list). */
export function ProjectList({ projects, showTeam = false }: { projects: Project[]; showTeam?: boolean }) {
  return (
    <ul className="plain-list">
      {projects.map((p) => (
        <li key={p.id}>
          <Link to={`/projects/${p.id}`} className="row row-link">
            <div>
              <strong>
                <span className="project-key">{p.key}</span> {p.name}
              </strong>
              <p className="muted small">
                {METHODOLOGY_LABELS[p.methodology]} · {p.memberCount} thành viên
                {showTeam && ` · team ${p.teamName}`}
                {p.myRole && ` · bạn là ${p.myRoles.map((r) => PROJECT_ROLE_LABELS[r]).join(', ')}`}
              </p>
            </div>
            <span className={`badge status-${p.status.toLowerCase()}`}>{PROJECT_STATUS_LABELS[p.status]}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
