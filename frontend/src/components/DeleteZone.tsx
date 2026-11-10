import { useState, type FormEvent } from 'react'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import { Alert, Field } from './Form'

/** Irreversible delete: the caller has to type the exact name before the button works. The server checks it again. */
export function DeleteZone({ title, name, warning, path, onDeleted }: { title: string; name: string; warning: string; path: string; onDeleted: () => void }) {
  const [open, setOpen] = useState(false)
  const [typed, setTyped] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(path, { method: 'DELETE', body: { name: typed } })
      onDeleted()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <section className="card stack delete-zone">
      <h2>{title}</h2>
      <p className="muted">{warning}</p>
      {!open ? (
        <button type="button" className="button-danger" onClick={() => setOpen(true)}>
          {title}
        </button>
      ) : (
        <form onSubmit={submit} className="stack">
          <Field label={`Nhập "${name}" để xác nhận`} value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
          <Alert>{error}</Alert>
          <div className="row">
            <button type="submit" className="button-danger" disabled={busy || typed.trim() !== name.trim()}>
              Xóa vĩnh viễn
            </button>
            <button type="button" className="button-secondary" onClick={() => { setOpen(false); setTyped('') }}>
              Hủy
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
