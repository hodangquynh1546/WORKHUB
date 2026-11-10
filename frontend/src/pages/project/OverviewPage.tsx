import { useState } from 'react'
import type { ProjectMember, ProjectRole, ProjectStats } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'
import { formatDay, METHODOLOGY_LABELS, PROJECT_ROLE_LABELS, PROJECT_ROLES, PROJECT_STATUS_LABELS, scheduleProgress } from '../roles'
import { useProject } from './projectContext'

/** Today's date in the browser's timezone as yyyy-mm-dd. */
function localIsoDate(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function Stat({ label, value, warn = false }: { label: string; value: number | string; warn?: boolean }) {
  return (
    <div className={`stat ${warn ? 'stat-warn' : ''}`}>
      <strong>{value}</strong>
      <span className="muted small">{label}</span>
    </div>
  )
}

const LEADERSHIP: ProjectRole[] = ['PROJECT_MANAGER', 'PRODUCT_OWNER', 'SCRUM_MASTER']

export function OverviewPage() {
  const { project } = useProject()
  const { data: members, error } = useResource<ProjectMember[]>(`/api/projects/${project.id}/members`)
  const progress = scheduleProgress(project.startDate, project.endDate)
  // Overdue is date-based, so let the browser say which day it is (fixed for the life of the page).
  const [todayIso] = useState(localIsoDate)
  const { data: stats } = useResource<ProjectStats>(`/api/projects/${project.id}/stats?today=${todayIso}`)

  return (
    <div className="two-column">
      <div className="stack-lg">
        <section className="card stack">
          <h2>Thông tin dự án</h2>
          {project.description && <p>{project.description}</p>}
          <dl className="facts">
            <dt>Mã dự án</dt>
            <dd>{project.key}</dd>
            <dt>Trạng thái</dt>
            <dd>{PROJECT_STATUS_LABELS[project.status]}</dd>
            <dt>Phương pháp</dt>
            <dd>{METHODOLOGY_LABELS[project.methodology]}</dd>
            <dt>Team</dt>
            <dd>{project.teamName}</dd>
            <dt>Bắt đầu</dt>
            <dd>{formatDay(project.startDate)}</dd>
            <dt>Kết thúc dự kiến</dt>
            <dd>{formatDay(project.endDate)}</dd>
          </dl>
        </section>

        <section className="card stack">
          <h2>Công việc</h2>
          {stats && (
            <>
              <div className="stat-grid">
                <Stat label="Tổng số" value={stats.total} />
                <Stat label="Chưa bắt đầu" value={stats.toDo} />
                <Stat label="Đang thực hiện" value={stats.inProgress} />
                <Stat label="Hoàn thành" value={stats.done} />
                <Stat label="Quá hạn" value={stats.overdue} warn={stats.overdue > 0} />
                <Stat label="Story Points xong" value={`${stats.donePoints}/${stats.totalPoints}`} />
              </div>
              <p className="muted small">Không tính Epic. Quá hạn = có hạn đã qua mà chưa hoàn thành.</p>
            </>
          )}
        </section>

        <section className="card stack">
          <h2>Tiến độ thời gian</h2>
          {progress ? (
            <>
              <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.percent}>
                <div className="progress-bar" style={{ width: `${progress.percent}%` }} />
              </div>
              <p className="muted">
                {progress.notStarted
                  ? `Chưa bắt đầu (${progress.totalDays} ngày kế hoạch).`
                  : progress.finished
                    ? `Đã qua ngày kết thúc dự kiến (${progress.totalDays} ngày kế hoạch).`
                    : `Ngày ${progress.elapsedDays}/${progress.totalDays} · còn ${progress.remainingDays} ngày (${progress.percent}%).`}
              </p>
              <p className="muted small">Đây là tiến độ theo lịch, chưa phải tiến độ công việc. Thống kê Task và Sprint sẽ có khi hoàn thành Backlog và Sprint.</p>
            </>
          ) : (
            <p className="muted">Đặt ngày bắt đầu và kết thúc trong phần Cài đặt để theo dõi tiến độ theo lịch.</p>
          )}
        </section>
      </div>

      <div className="stack-lg">
        <section className="card stack">
          <h2>Đội ngũ chính</h2>
          <Alert>{error}</Alert>
          {members &&
            LEADERSHIP.map((role) => {
              const holders = members.filter((m) => m.role === role)
              return (
                <div key={role} className="leader-row">
                  <span className="muted small">{PROJECT_ROLE_LABELS[role]}</span>
                  <strong>{holders.length ? holders.map((h) => h.fullName).join(', ') : 'Chưa có'}</strong>
                </div>
              )
            })}
        </section>

        <section className="card stack">
          <h2>Thành viên theo vai trò</h2>
          {members && (
            <ul className="plain-list">
              {PROJECT_ROLES.map((role) => (
                <li key={role} className="row">
                  <span>{PROJECT_ROLE_LABELS[role]}</span>
                  <strong>{members.filter((m) => m.role === role).length}</strong>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
