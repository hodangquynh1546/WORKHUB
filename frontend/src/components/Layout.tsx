import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { useRealtime } from '../realtime/RealtimeContext'
import { useUnread } from '../realtime/useUnread'
import { AccountMenu } from './AccountMenu'
import { NotificationBell } from './NotificationBell'
import { Toasts } from './Toasts'

/** Shell for signed-in pages: top bar, email-verification banner, page content. */
export function Layout() {
  const { user } = useAuth()
  const [resendState, setResendState] = useState<string | null>(null)
  const unread = useUnread()
  const { status } = useRealtime()
  // A brief reconnect is normal; only say something when the connection has been down for a moment.
  const [offline, setOffline] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => setOffline(status === 'connecting'), status === 'connecting' ? 3000 : 0)
    return () => clearTimeout(timer)
  }, [status])

  async function resendVerification() {
    try {
      await api('/api/me/email-verification', { method: 'POST' })
      setResendState('Đã gửi lại email xác thực. (Môi trường dev: liên kết nằm trong log của backend.)')
    } catch (error) {
      setResendState(errorMessage(error))
    }
  }

  return (
    <>
      <header className="topbar">
        <NavLink to="/workspaces" className="brand">
          WORKHUB
        </NavLink>
        <nav>
          <NavLink to="/workspaces">Workspaces</NavLink>
          <NavLink to="/teams">Team</NavLink>
          <NavLink to="/projects" end>
            Dự án
          </NavLink>
          <NavLink to="/chat">
            Trò chuyện
            {unread.chat > 0 && <span className="badge-count nav-count">{unread.chat > 99 ? '99+' : unread.chat}</span>}
          </NavLink>
        </nav>
        {offline && (
          <span className="conn-status" role="status" title="Dữ liệu trực tiếp tạm dừng; app sẽ tự nối lại">
            Đang nối lại…
          </span>
        )}
        <NotificationBell unread={unread.notifications} onUnreadChange={unread.setNotifications} />
        <AccountMenu />
      </header>
      {user && !user.emailVerified && (
        <div className="banner">
          Email <strong>{user.email}</strong> chưa được xác thực.{' '}
          <button type="button" className="link-button" onClick={resendVerification}>
            Gửi lại email xác thực
          </button>
          {resendState && <span className="banner-note">{resendState}</span>}
        </div>
      )}
      <main className="page">
        <Outlet />
      </main>
      <Toasts />
    </>
  )
}
