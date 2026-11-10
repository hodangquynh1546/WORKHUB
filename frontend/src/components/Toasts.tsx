import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import type { AppNotification } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useRealtime } from '../realtime/RealtimeContext'

type Toast = AppNotification & { shownAt: number }

/** A small card in the corner when a notification arrives while the app is open; it fades after a few seconds. */
export function Toasts() {
  const { on } = useRealtime()
  const { user } = useAuth()
  const [toasts, setToasts] = useState<Toast[]>([])

  useEffect(
    () =>
      on('notification', (frame) => {
        const notification = frame.notification as AppNotification | undefined
        if (!notification || notification.actorId === user?.id) return
        const toast = { ...notification, shownAt: Date.now() }
        setToasts((list) => [toast, ...list].slice(0, 3))
        setTimeout(() => setToasts((list) => list.filter((t) => t.id !== toast.id)), 7000)
      }),
    [on, user?.id],
  )

  if (toasts.length === 0) return null
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((t) => (
        <Link key={t.id} to={t.link ?? '/notifications'} className={`toast ${t.alert ? 'toast-alert' : ''}`} onClick={() => setToasts((list) => list.filter((x) => x.id !== t.id))}>
          <strong>{t.title}</strong>
          {t.body && <span>{t.body}</span>}
        </Link>
      ))}
    </div>
  )
}
