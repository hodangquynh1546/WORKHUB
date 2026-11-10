import { createContext, useContext } from 'react'
import type { UserProfile } from '../api/types'

export type AuthContextValue = {
  /** False until the startup refresh attempt has finished. */
  ready: boolean
  user: UserProfile | null
  login: (email: string, password: string) => Promise<void>
  register: (fullName: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  setUser: (user: UserProfile) => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}
