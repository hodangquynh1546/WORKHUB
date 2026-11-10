import type { ReactNode } from 'react'

/** Centered card used by the signed-out pages. */
export function AuthCard({ title, children, footer }: { title: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <main className="auth-page">
      <div className="card auth-card">
        <p className="brand">WORKHUB</p>
        <h1>{title}</h1>
        {children}
      </div>
      {footer && <p className="auth-footer">{footer}</p>}
    </main>
  )
}
