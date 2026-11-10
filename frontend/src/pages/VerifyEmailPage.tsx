import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { UserProfile } from '../api/types'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { Alert } from '../components/Form'

export function VerifyEmailPage() {
  const token = useSearchParams()[0].get('token')
  const { ready, user, setUser } = useAuth()
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null)
  // Tokens are single-use: StrictMode runs effects twice in dev, so make sure we only send it once.
  const sent = useRef(false)

  useEffect(() => {
    if (!ready || !token || sent.current) return
    sent.current = true
    api('/api/auth/verify-email', { method: 'POST', body: { token } })
      .then(async () => {
        setResult({ ok: true, message: 'Email của bạn đã được xác thực.' })
        if (user) setUser(await api<UserProfile>('/api/me'))
      })
      .catch((err) => setResult({ ok: false, message: errorMessage(err) }))
  }, [ready, token, user, setUser])

  return (
    <AuthCard title="Xác thực email" footer={<Link to={user ? '/workspaces' : '/login'}>Tiếp tục</Link>}>
      {!token ? (
        <Alert>Liên kết không hợp lệ.</Alert>
      ) : result ? (
        <Alert kind={result.ok ? 'success' : 'error'}>{result.message}</Alert>
      ) : (
        <p className="muted">Đang xác thực…</p>
      )}
    </AuthCard>
  )
}
