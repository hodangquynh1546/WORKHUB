import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import type { AppNotification, NotificationPage } from '../api/types'
import { timeAgo } from '../pages/notificationLabels'
import { useRealtime } from '../realtime/RealtimeContext'

/** The bell in the top bar: unread badge, a panel with the latest notifications, mark-all-read. */
export function NotificationBell({ unread, onUnreadChange }: { unread: number; onUnreadChange: (n: number) => void }) {
  const navigate = useNavigate()
  const { on } = useRealtime()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<AppNotification[] | null>(null)
  const root = useRef<HTMLDivElement>(null)

  const load = useCallback(async () => {
    try {
      const page = await api<NotificationPage>('/api/notifications?size=10')
      setItems(page.items)
      onUnreadChange(page.unread)
    } catch {
      setItems([])
    }
  }, [onUnreadChange])

  useEffect(() => {
    if (!open) return
    // Loaded when the panel opens and again whenever something new arrives while it is open.
    const timer = setTimeout(() => void load(), 0)
    const off = on('notification', () => void load())
    const onPointer = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      clearTimeout(timer)
      off()
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, load, on])

  async function openItem(n: AppNotification) {
    setOpen(false)
    if (!n.read) {
      await api(`/api/notifications/${n.id}/read`, { method: 'POST' }).catch(() => {})
      onUnreadChange(Math.max(0, unread - 1))
    }
    if (n.link) navigate(n.link)
  }

  async function readAll() {
    await api('/api/notifications/read-all', { method: 'POST' }).catch(() => {})
    onUnreadChange(0)
    setItems((list) => list?.map((n) => ({ ...n, read: true })) ?? null)
  }

  return (
    <div className="bell" ref={root}>
      <button type="button" className="bell-trigger button-secondary" aria-haspopup="dialog" aria-expanded={open} aria-label={unread > 0 ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'} onClick={() => setOpen((v) => !v)}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8" />
          <path d="M10.3 20a2 2 0 0 0 3.4 0" />
        </svg>
        {unread > 0 && <span className="badge-count">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <section className="bell-panel" role="dialog" aria-label="Thông báo">
          <div className="section-header">
            <h2>Thông báo</h2>
            {unread > 0 && (
              <button type="button" className="link-button small" onClick={readAll}>
                Đánh dấu đã đọc tất cả
              </button>
            )}
          </div>
          {items === null && <p className="muted small">Đang tải…</p>}
          {items?.length === 0 && <p className="muted">Chưa có thông báo nào. Khi bạn được giao việc, được nhắc đến hay có cảnh báo tiến độ, chúng sẽ hiện ở đây.</p>}
          <ul className="plain-list notif-list">
            {items?.map((n) => (
              <li key={n.id}>
                <button type="button" className={`notif-item ${n.read ? '' : 'unread'}`} onClick={() => openItem(n)}>
                  <span className="notif-title">{n.title}</span>
                  {n.body && <span className="notif-body">{n.body}</span>}
                  <span className="muted small">{timeAgo(n.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
          <Link to="/notifications" className="bell-all" onClick={() => setOpen(false)}>
            Xem tất cả và cài đặt
          </Link>
        </section>
      )}
    </div>
  )
}
