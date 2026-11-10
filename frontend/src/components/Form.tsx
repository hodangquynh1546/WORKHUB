import { useState, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'

export function Field({ label, ...input }: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  const [visible, setVisible] = useState(false)
  if (input.type !== 'password') {
    return (
      <label className="field">
        <span>{label}</span>
        <input {...input} />
      </label>
    )
  }
  return (
    <label className="field">
      <span>{label}</span>
      <div className="password-wrap">
        <input {...input} type={visible ? 'text' : 'password'} />
        <button
          type="button"
          className="password-toggle"
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
          aria-pressed={visible}
          onClick={() => setVisible((v) => !v)}
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
            <circle cx="12" cy="12" r="3" />
            {visible && <path d="M3 3l18 18" />}
          </svg>
        </button>
      </div>
    </label>
  )
}

export function TextArea({ label, ...input }: { label: string } & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <label className="field">
      <span>{label}</span>
      <textarea rows={4} {...input} />
    </label>
  )
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success' | 'info'; children: ReactNode }) {
  if (!children) return null
  return (
    <p className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </p>
  )
}
