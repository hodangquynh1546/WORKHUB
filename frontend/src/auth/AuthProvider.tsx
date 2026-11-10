import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, applySession, onSessionChange, refreshSession } from '../api/client'
import type { AuthResponse, UserProfile } from '../api/types'
import { AuthContext, type AuthContextValue } from './AuthContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    // Keeps `user` in sync with every session change, including a failed background refresh.
    onSessionChange((session) => setUser(session?.user ?? null))
    refreshSession().finally(() => setReady(true))
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      ready,
      user,
      setUser,
      login: async (email, password) => {
        applySession(await api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } }))
      },
      register: async (fullName, email, password) => {
        applySession(
          await api<AuthResponse>('/api/auth/register', { method: 'POST', body: { fullName, email, password } }),
        )
      },
      logout: async () => {
        await api('/api/auth/logout', { method: 'POST' }).catch(() => undefined)
        applySession(null)
      },
    }),
    [ready, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
