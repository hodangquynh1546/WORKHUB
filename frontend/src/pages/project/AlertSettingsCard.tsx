import { useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { AlertSettings } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'

type Message = { ok: boolean; text: string } | null

export function AlertSettingsCard({ projectId }: { projectId: string }) {
  const { data, error } = useResource<AlertSettings>(`/api/projects/${projectId}/alert-settings`)
  return data ? <Form key={JSON.stringify(data)} projectId={projectId} initial={data} /> : <section className="card">{error ? <Alert>{error}</Alert> : <p className="muted">Đang tải…</p>}</section>
}

function Form({ projectId, initial }: { projectId: string; initial: AlertSettings }) {
  const [v, setV] = useState(initial)
  const [message, setMessage] = useState<Message>(null)
  const [busy, setBusy] = useState(false)
  const set = (patch: Partial<AlertSettings>) => setV((s) => ({ ...s, ...patch }))
  const num = (value: string) => Math.max(0, Number.parseInt(value, 10) || 0)

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      await api(`/api/projects/${projectId}/alert-settings`, { method: 'PUT', body: v })
      setMessage({ ok: true, text: 'Đã lưu ngưỡng cảnh báo.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h2>Ngưỡng cảnh báo</h2>
      <p className="muted small">Đặt 0 để tắt một quy tắc. Ngưỡng vừa phải giúp tránh gửi quá nhiều thông báo.</p>
      <form onSubmit={save} className="stack">
        <label className="inline-check alert-toggle">
          <input type="checkbox" checked={v.overdueEnabled} onChange={(e) => set({ overdueEnabled: e.target.checked })} /> Báo công việc quá hạn
        </label>
        <label className="inline-check alert-toggle">
          <input type="checkbox" checked={v.blockedEnabled} onChange={(e) => set({ blockedEnabled: e.target.checked })} /> Báo khi công việc bị đánh dấu Blocked
        </label>
        <div className="inline-form">
          <label className="field">
            <span>Sắp đến hạn trong (ngày)</span>
            <input type="number" min={0} max={30} value={v.dueSoonDays} onChange={(e) => set({ dueSoonDays: num(e.target.value) })} />
          </label>
          <label className="field">
            <span>Không cập nhật quá (ngày)</span>
            <input type="number" min={0} max={90} value={v.noProgressDays} onChange={(e) => set({ noProgressDays: num(e.target.value) })} />
          </label>
        </div>
        <div className="inline-form">
          <label className="field">
            <span>Sprint nguy cơ: còn ≤ (ngày)</span>
            <input type="number" min={0} max={30} value={v.sprintRiskDaysLeft} onChange={(e) => set({ sprintRiskDaysLeft: num(e.target.value) })} />
          </label>
          <label className="field">
            <span>…mà chưa xong ≥ (%)</span>
            <input type="number" min={0} max={100} value={v.sprintRiskOpenPct} onChange={(e) => set({ sprintRiskOpenPct: num(e.target.value) })} />
          </label>
        </div>
        <div className="inline-form">
          <label className="field">
            <span>Quá tải: hơn (việc mỗi người)</span>
            <input type="number" min={0} max={200} value={v.overloadOpenIssues} onChange={(e) => set({ overloadOpenIssues: num(e.target.value) })} />
          </label>
          <label className="field">
            <span>Phạm vi sprint tăng ≥ (%)</span>
            <input type="number" min={0} max={500} value={v.scopeChangePct} onChange={(e) => set({ scopeChangePct: num(e.target.value) })} />
          </label>
        </div>
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit" disabled={busy}>
          Lưu ngưỡng
        </button>
      </form>
    </section>
  )
}
