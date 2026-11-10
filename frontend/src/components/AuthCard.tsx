import type { ReactNode } from 'react'

/** Signed-out pages: a black brand panel beside the form (the panel collapses to a header on phones). */
export function AuthCard({ title, children, footer }: { title: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <main className="auth-page">
      <aside className="auth-aside">
        <p className="brand">WORKHUB</p>
        <p className="auth-tagline">Mọi việc của đội, rõ ràng trong một nơi.</p>
      </aside>
      <section className="auth-main">
        <div className="auth-card">
          <h1>{title}</h1>
          {children}
        </div>
        {footer && <p className="auth-footer">{footer}</p>}
      </section>
    </main>
  )
}
