import { useEffect, useRef, useState } from 'react'
import { downloadFile } from '../api/client'
import { errorMessage } from '../api/errors'

export type ExportOption = { label: string; path: string }

/**
 * "Xuất" button with a small menu: each option is a data set with CSV and Excel downloads. `path` is the endpoint
 * without `format`; query parameters it already has are kept. Downloads carry the token (a plain link cannot).
 */
export function ExportMenu({ options, label = 'Xuất dữ liệu' }: { options: ExportOption[]; label?: string }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  async function download(option: ExportOption, format: 'csv' | 'xlsx') {
    const key = `${option.label}-${format}`
    setBusy(key)
    setError(null)
    try {
      await downloadFile(`${option.path}${option.path.includes('?') ? '&' : '?'}format=${format}`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="export-menu no-print" ref={root}>
      <button type="button" className="button-secondary button-small" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        {label}
      </button>
      {open && (
        <div className="export-panel" role="menu">
          {options.map((o) => (
            <div key={o.label} className="export-row" role="none">
              <span>{o.label}</span>
              <span className="actions">
                {(['csv', 'xlsx'] as const).map((f) => (
                  <button key={f} type="button" role="menuitem" className="button-secondary button-small" disabled={busy !== null} onClick={() => download(o, f)}>
                    {busy === `${o.label}-${f}` ? 'Đang tải…' : f === 'csv' ? 'CSV' : 'Excel'}
                  </button>
                ))}
              </span>
            </div>
          ))}
          {error && <p className="text-danger small">{error}</p>}
        </div>
      )}
    </div>
  )
}

/** Opens the browser's print dialog; "Save as PDF" there gives a PDF of the page without the app chrome (see print CSS). */
export function PrintButton({ label = 'Xuất PDF' }: { label?: string }) {
  return (
    <button type="button" className="button-secondary button-small no-print" onClick={() => window.print()}>
      {label}
    </button>
  )
}
