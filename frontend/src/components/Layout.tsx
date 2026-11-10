import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { AccountMenu } from './AccountMenu'

/** Shell for signed-in pages: top bar, email-verification banner, page content. */
export function Layout() {
  const { user } = useAuth()
  const [resendState, setResendState] = useState<string | null>(null)

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
        </nav>
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
    </>
  )
}
