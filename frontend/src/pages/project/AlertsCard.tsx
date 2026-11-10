import { Link } from 'react-router'
import type { ProjectAlert } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'
import { useLiveReload } from '../../realtime/useLiveReload'

const SEVERITY: Record<ProjectAlert['severity'], { label: string; className: string }> = {
  CRITICAL: { label: 'Nghiêm trọng', className: 'alert-sev-critical' },
  WARNING: { label: 'Cần chú ý', className: 'alert-sev-warning' },
  INFO: { label: 'Lưu ý', className: 'alert-sev-info' },
}

/** The risks the progress rules see right now. They are signals to look at, not a verdict on the sprint. */
export function AlertsCard({ projectId }: { projectId: string }) {
  const { data, error, reload } = useResource<ProjectAlert[]>(`/api/projects/${projectId}/alerts`)
  useLiveReload(projectId, ['issues', 'sprints', 'project'], reload)

  return (
    <section className="card stack">
      <h2>Cảnh báo tiến độ</h2>
      <Alert>{error}</Alert>
      {data?.length === 0 && <p className="muted">Hiện chưa có rủi ro nào theo các ngưỡng đã đặt. Việc quá hạn, bị chặn, quá tải hay sprint có nguy cơ sẽ hiện ở đây.</p>}
      <ul className="plain-list">
        {data?.map((a, i) => (
          <li key={`${a.kind}-${a.subjectId}-${i}`} className="row alert-row">
            <div>
              {a.link ? <Link to={a.link}>{a.title}</Link> : <strong>{a.title}</strong>}
              {a.detail && <p className="muted small">{a.detail}</p>}
            </div>
            <span className={`badge ${SEVERITY[a.severity].className}`}>{SEVERITY[a.severity].label}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
