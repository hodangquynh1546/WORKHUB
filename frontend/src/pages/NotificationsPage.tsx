import { useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { NotificationPage, NotificationPreference, NotificationType } from '../api/types'
import { useResource } from '../api/useResource'
import { Alert } from '../components/Form'
import { ListSkeleton } from '../components/Skeleton'
import { useRealtime } from '../realtime/RealtimeContext'
import { NOTIFICATION_LABELS, timeAgo } from './notificationLabels'
import { useEffect } from 'react'

export function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [size, setSize] = useState(30)
  const { data, error, reload } = useResource<NotificationPage>(`/api/notifications?size=${size}${unreadOnly ? '&unread=true' : ''}`)
  const { on } = useRealtime()

  useEffect(() => on('notification', reload), [on, reload])

  async function readAll() {
    await api('/api/notifications/read-all', { method: 'POST' })
    reload()
  }

  async function open(id: string) {
    await api(`/api/notifications/${id}/read`, { method: 'POST' }).catch(() => {})
    reload()
  }

  return (
    <div className="stack-lg">
      <h1>Thông báo</h1>
      <div className="two-column">
        <section className="stack">
          <div className="filters">
            <div className="segmented-control" role="group" aria-label="Lọc">
              <button type="button" className={!unreadOnly ? 'on' : ''} onClick={() => setUnreadOnly(false)}>
                Tất cả
              </button>
              <button type="button" className={unreadOnly ? 'on' : ''} onClick={() => setUnreadOnly(true)}>
                Chưa đọc{data && data.unread > 0 ? ` (${data.unread})` : ''}
              </button>
            </div>
            {data && data.unread > 0 && (
              <button type="button" className="button-secondary button-small docs-new" onClick={readAll}>
                Đánh dấu đã đọc tất cả
              </button>
            )}
          </div>
          <Alert>{error}</Alert>
          {!data && !error && <ListSkeleton />}
          {data?.items.length === 0 && (
            <section className="card empty-state">
              <h2>{unreadOnly ? 'Bạn đã đọc hết' : 'Chưa có thông báo nào'}</h2>
              <p className="muted">Thông báo xuất hiện khi bạn được giao việc, được nhắc đến, có cuộc họp sắp tới hoặc cảnh báo tiến độ.</p>
            </section>
          )}
          <ul className="plain-list doc-list">
            {data?.items.map((n) => {
              const body = (
                <>
                  <div className="doc-card-title">
                    <strong>{n.title}</strong>
                    {n.alert && <span className="badge status-on_hold">Cảnh báo</span>}
                    {!n.read && <span className="badge">Mới</span>}
                  </div>
                  {n.body && <p className="muted notif-body">{n.body}</p>}
                  <p className="muted small">{timeAgo(n.createdAt)}</p>
                </>
              )
              return (
                <li key={n.id}>
                  {n.link ? (
                    <Link to={n.link} className={`card doc-card ${n.read ? '' : 'notif-unread'}`} onClick={() => open(n.id)}>
                      {body}
                    </Link>
                  ) : (
                    <div className={`card ${n.read ? '' : 'notif-unread'}`}>{body}</div>
                  )}
                </li>
              )
            })}
          </ul>
          {data && data.total > data.items.length && (
            <button type="button" className="button-secondary" onClick={() => setSize((s) => s + 30)}>
              Xem thêm ({data.items.length}/{data.total})
            </button>
          )}
        </section>
        <Preferences />
      </div>
    </div>
  )
}

function Preferences() {
  const { data, error, reload } = useResource<NotificationPreference[]>('/api/notification-preferences')
  const [message, setMessage] = useState<string | null>(null)

  async function change(type: NotificationType, inApp: boolean, email: boolean) {
    setMessage(null)
    try {
      await api('/api/notification-preferences', { method: 'PUT', body: { type, inApp, email } })
      reload()
    } catch (err) {
      setMessage(errorMessage(err))
    }
  }

  const group = (alert: boolean) => data?.filter((p) => p.alert === alert) ?? []
  return (
    <section className="card stack prefs">
      <h2>Cách nhận thông báo</h2>
      <p className="muted small">Chọn từng loại có hiện trong ứng dụng, gửi email, cả hai hoặc không nhận.</p>
      <Alert>{error ?? message}</Alert>
      {[false, true].map((alert) => (
        <div key={String(alert)}>
          <h3>{alert ? 'Cảnh báo tiến độ' : 'Hoạt động'}</h3>
          <table className="table prefs-table">
            <thead>
              <tr>
                <th>Loại</th>
                <th>Trong app</th>
                <th>Email</th>
              </tr>
            </thead>
            <tbody>
              {group(alert).map((p) => (
                <tr key={p.type}>
                  <td>
                    <strong>{NOTIFICATION_LABELS[p.type].name}</strong>
                    <p className="muted small">{NOTIFICATION_LABELS[p.type].hint}</p>
                  </td>
                  <td>
                    <input type="checkbox" checked={p.inApp} aria-label={`${NOTIFICATION_LABELS[p.type].name}: trong app`} onChange={(e) => change(p.type, e.target.checked, p.email)} />
                  </td>
                  <td>
                    <input type="checkbox" checked={p.email} aria-label={`${NOTIFICATION_LABELS[p.type].name}: email`} onChange={(e) => change(p.type, p.inApp, e.target.checked)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </section>
  )
}
