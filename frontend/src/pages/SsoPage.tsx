import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, applySession } from '../api/client'
import type { AuthResponse } from '../api/types'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { Alert } from '../components/Form'

/**
 * Landing page of "Mở WorkHub" from SmartSpec: the one-time ticket signs this browser in as the same person (a
 * different WorkHub session left open would otherwise show "project not found"), then goes to `next`.
 */
export function SsoPage() {
  const params = useSearchParams()[0]
  const ticket = params.get('ticket')
  const rawNext = params.get('next') ?? '/workspaces'
  const next = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/workspaces' // same-origin paths only
  const { ready } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const sent = useRef(false) // single-use ticket; StrictMode runs effects twice in dev

  useEffect(() => {
    if (!ready || sent.current) return
    sent.current = true
    if (!ticket) {
      navigate(next, { replace: true })
      return
    }
    api<AuthResponse>('/api/auth/sso', { method: 'POST', body: { ticket } })
      .then((session) => {
        applySession(session)
        navigate(next, { replace: true })
      })
      .catch((err) => setError(errorMessage(err)))
  }, [ready, ticket, next, navigate])

  return (
    <AuthCard title="Đang đăng nhập…" footer={<Link to="/login">Đăng nhập thủ công</Link>}>
      {error ? <Alert>{error}</Alert> : <p>Đang đăng nhập WorkHub bằng tài khoản SmartSpec của bạn…</p>}
    </AuthCard>
  )
}
