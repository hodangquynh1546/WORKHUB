import { Link, NavLink, Outlet, useParams } from 'react-router'
import type { Project } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'
import { METHODOLOGY_LABELS, PROJECT_STATUS_LABELS } from '../roles'
import type { ProjectContext } from './projectContext'

/** Areas that arrive in later milestones; shown so the project's shape is visible. */
const COMING_SOON = ['Tài liệu', 'Cuộc họp', 'Lịch', 'Báo cáo']

export function ProjectLayout() {
  const { projectId } = useParams()
  const { data: project, error, reload } = useResource<Project>(`/api/projects/${projectId}`)

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to="/workspaces">← Danh sách workspace</Link>
      </>
    )
  }
  if (!project) return <p className="muted">Đang tải…</p>

  const context: ProjectContext = { project, reload }
  const canUpdate = project.permissions.includes('UPDATE_PROJECT')

  return (
    <>
      <Link to={`/teams/${project.teamId}`} className="muted-link">
        ← Team {project.teamName}
      </Link>
      <header className="workspace-header">
        <div>
          <h1>
            <span className="project-key">{project.key}</span> {project.name}
          </h1>
          <p className="muted">
            {METHODOLOGY_LABELS[project.methodology]} · {project.memberCount} thành viên
          </p>
        </div>
        <span className={`badge status-${project.status.toLowerCase()}`}>{PROJECT_STATUS_LABELS[project.status]}</span>
      </header>
      <nav className="tabs" aria-label="Khu vực dự án">
        <NavLink to="" end>
          Tổng quan
        </NavLink>
        <NavLink to="backlog">Backlog</NavLink>
        <NavLink to="sprints">Sprint</NavLink>
        <NavLink to="board">Board</NavLink>
        <NavLink to="members">Thành viên</NavLink>
        {canUpdate && <NavLink to="settings">Cài đặt</NavLink>}
        {COMING_SOON.map((label) => (
          <span key={label} className="tab-disabled" title="Sắp có">
            {label}
          </span>
        ))}
      </nav>
      {project.status === 'ARCHIVED' && (
        <Alert kind="info">Dự án đã được lưu trữ: chỉ xem. Quản lý dự án có thể đổi trạng thái trong phần Cài đặt để mở lại.</Alert>
      )}
      <Outlet context={context} />
    </>
  )
}
