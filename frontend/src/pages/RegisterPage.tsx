import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { Alert, Field } from '../components/Form'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/workspaces'
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (password !== confirm) {
      setError('Mật khẩu nhập lại không khớp.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await register(fullName.trim(), email.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <AuthCard
      title="Tạo tài khoản"
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" state={location.state}>
            Đăng nhập
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="stack">
        <Field label="Họ tên" autoComplete="name" required maxLength={150} value={fullName} onChange={(e) => setFullName(e.target.value)} />
        <Field label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field
          label="Mật khẩu (tối thiểu 8 ký tự)"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Field
          label="Nhập lại mật khẩu"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
        <Alert>{error}</Alert>
        <button type="submit" disabled={busy}>
          {busy ? 'Đang tạo tài khoản…' : 'Đăng ký'}
        </button>
      </form>
    </AuthCard>
  )
}
