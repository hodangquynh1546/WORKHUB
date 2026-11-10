import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Methodology, Project } from '../api/types'
import { METHODOLOGY_LABELS } from '../pages/roles'
import { Alert, Field } from './Form'

/** Creates a project in `teamId`; the creator becomes its Project Manager. */
export function CreateProjectForm({ teamId }: { teamId: string }) {
  const navigate = useNavigate()
  const [key, setKey] = useState('')
  const [name, setName] = useState('')
  const [methodology, setMethodology] = useState<Methodology>('SCRUM')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const project = await api<Project>(`/api/teams/${teamId}/projects`, {
        method: 'POST',
        body: { key, name, methodology, startDate: startDate || null, endDate: endDate || null },
      })
      navigate(`/projects/${project.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="stack">
      <div className="inline-form">
        <Field
          label="Mã dự án"
          placeholder="ECOM"
          required
          minLength={2}
          maxLength={10}
          pattern="[A-Za-z][A-Za-z0-9]{1,9}"
          title="2-10 chữ cái hoặc số, bắt đầu bằng chữ cái"
          value={key}
          onChange={(e) => setKey(e.target.value.toUpperCase())}
        />
        <Field label="Tên dự án" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div className="inline-form">
        <label className="field">
          <span>Phương pháp</span>
          <select value={methodology} onChange={(e) => setMethodology(e.target.value as Methodology)}>
            {Object.entries(METHODOLOGY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <Field label="Bắt đầu" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        <Field label="Kết thúc" type="date" min={startDate || undefined} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
      </div>
      <p className="muted small">Mã dự án không đổi được sau khi tạo vì sẽ dùng làm mã công việc (ví dụ ECOM-12).</p>
      <Alert>{error}</Alert>
      <button type="submit" disabled={busy}>
        Tạo dự án
      </button>
    </form>
  )
}
