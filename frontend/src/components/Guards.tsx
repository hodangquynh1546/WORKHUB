import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'

function Loading() {
  return <p className="loading">Đang tải…</p>
}

/** Signed-in only; otherwise sends the user to /login and back here afterwards. */
export function RequireAuth() {
  const { ready, user } = useAuth()
  const location = useLocation()
  if (!ready) return <Loading />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** Signed-out only (login, register): a signed-in user goes straight to the app. */
export function RedirectIfAuthenticated() {
  const { ready, user } = useAuth()
  if (!ready) return <Loading />
  if (user) return <Navigate to="/workspaces" replace />
  return <Outlet />
}
