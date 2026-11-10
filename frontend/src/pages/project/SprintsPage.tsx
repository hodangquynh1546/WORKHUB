import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ProjectMember, Sprint } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert, Field, TextArea } from '../../components/Form'
import { SPRINT_STATUS_LABELS } from '../issueLabels'
import { formatDay, localIsoDate } from '../roles'
import { useProject } from './projectContext'
import { useLiveReload } from '../../realtime/useLiveReload'

function SprintRow({ sprint, projectId }: { sprint: Sprint; projectId: string }) {
  return (
    <li>
      <Link to={`/projects/${projectId}/sprints/${sprint.id}`} className="row row-link">
        <div>
          <strong>{sprint.name}</strong>
          <p className="muted small">
            {formatDay(sprint.startDate)} → {formatDay(sprint.endDate)} · {sprint.issueCount} công việc · {sprint.donePoints}/{sprint.scopePoints} SP
            {sprint.facilitator && ` · điều phối: ${sprint.facilitator.fullName}`}
          </p>
          {sprint.goal && <p className="small">🎯 {sprint.goal}</p>}
        </div>
        <span className={`badge sprint-${sprint.status.toLowerCase()}`}>{SPRINT_STATUS_LABELS[sprint.status]}</span>
      </Link>
    </li>
  )
}

export function SprintsPage() {
  const { project } = useProject()
  const canManage = project.permissions.includes('MANAGE_SPRINTS') && project.status !== 'ARCHIVED'
  const { data: sprints, error, reload } = useResource<Sprint[]>(`/api/projects/${project.id}/sprints`)
  useLiveReload(project.id, ['sprints'], reload)
  const [creating, setCreating] = useState(false)

  if (project.methodology !== 'SCRUM') {
    return (
      <section className="card">
        <h2>Sprint</h2>
        <p className="muted">Dự án này dùng Kanban nên không có Sprint. Hãy xem Board để theo dõi công việc.</p>
      </section>
    )
  }

  const group = (statuses: Sprint['status'][]) => sprints?.filter((s) => statuses.includes(s.status)) ?? []
  const sections: [string, Sprint[]][] = [
    ['Đang chạy', group(['ACTIVE'])],
    ['Sắp tới', group(['FUTURE']).reverse()],
    ['Đã đóng', group(['COMPLETED', 'CANCELLED'])],
  ]

  return (
    <div className="stack-lg">
      <section className="card stack">
        <div className="section-header">
          <h2>Sprint</h2>
          {canManage && (
            <button type="button" className="button-secondary button-small" onClick={() => setCreating((v) => !v)}>
              {creating ? 'Đóng' : 'Tạo Sprint'}
            </button>
          )}
        </div>
        {creating && (
          <CreateSprintForm
            projectId={project.id}
            suggestedName={`Sprint ${(sprints?.length ?? 0) + 1}`}
            onCreated={() => {
              setCreating(false)
              reload()
            }}
          />
        )}
        <Alert>{error}</Alert>
        {sprints?.length === 0 && !creating && <p className="muted">Chưa có Sprint nào. Hãy tạo Sprint đầu tiên rồi đưa công việc từ Backlog vào.</p>}
      </section>
      {sections.map(
        ([title, list]) =>
          list.length > 0 && (
            <section key={title} className="card">
              <h2>{title}</h2>
              <ul className="plain-list">
                {list.map((s) => (
                  <SprintRow key={s.id} sprint={s} projectId={project.id} />
                ))}
              </ul>
            </section>
          ),
      )}
    </div>
  )
}

function CreateSprintForm({ projectId, suggestedName, onCreated }: { projectId: string; suggestedName: string; onCreated: () => void }) {
  const navigate = useNavigate()
  const { data: members } = useResource<ProjectMember[]>(`/api/projects/${projectId}/members`)
  const [name, setName] = useState(suggestedName)
  const [goal, setGoal] = useState('')
  const [startDate, setStartDate] = useState(() => localIsoDate())
  const [endDate, setEndDate] = useState(() => localIsoDate(13))
  const [facilitatorId, setFacilitatorId] = useState('')
  const [capacity, setCapacity] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const sprint = await api<Sprint>(`/api/projects/${projectId}/sprints`, {
        method: 'POST',
        body: { name, goal, startDate, endDate, facilitatorId: facilitatorId || null, capacityPoints: capacity === '' ? null : Number(capacity) },
      })
      onCreated()
      navigate(`/projects/${projectId}/sprints/${sprint.id}`)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <form onSubmit={submit} className="stack">
      <Field label="Tên Sprint" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      <TextArea label="Mục tiêu Sprint (Sprint Goal)" rows={2} maxLength={1000} value={goal} onChange={(e) => setGoal(e.target.value)} />
      <div className="inline-form">
        <Field label="Bắt đầu" type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        <Field label="Kết thúc (tối đa 1 tháng)" type="date" required min={startDate} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        <Field label="Năng lực (Story Points)" type="number" min={0} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
      </div>
      <label className="field">
        <span>Người điều phối</span>
        <select value={facilitatorId} onChange={(e) => setFacilitatorId(e.target.value)}>
          <option value="">Chưa chọn</option>
          {members?.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.fullName}
            </option>
          ))}
        </select>
      </label>
      <Alert>{error}</Alert>
      <button type="submit">Tạo Sprint</button>
    </form>
  )
}
