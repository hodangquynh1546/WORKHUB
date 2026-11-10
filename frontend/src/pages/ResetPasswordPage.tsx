import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import { AuthCard } from '../components/AuthCard'
import { Alert, Field } from '../components/Form'

export function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [done, setDone] = useState(false)
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
      await api('/api/auth/reset-password', { method: 'POST', body: { token, newPassword: password } })
      setDone(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <AuthCard title="Đặt lại mật khẩu" footer={<Link to="/forgot-password">Yêu cầu liên kết mới</Link>}>
        <Alert>Liên kết không hợp lệ.</Alert>
      </AuthCard>
    )
  }

  return (
    <AuthCard title="Đặt lại mật khẩu" footer={<Link to="/login">Quay lại đăng nhập</Link>}>
      {done ? (
        <Alert kind="success">
          Đã đổi mật khẩu. Mọi phiên đăng nhập cũ đã bị đăng xuất. <Link to="/login">Đăng nhập ngay</Link>
        </Alert>
      ) : (
        <form onSubmit={submit} className="stack">
          <Field
            label="Mật khẩu mới (tối thiểu 8 ký tự)"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Field
            label="Nhập lại mật khẩu mới"
            type="password"
            autoComplete="new-password"
            required
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
          <Alert>{error}</Alert>
          <button type="submit" disabled={busy}>
            Đặt lại mật khẩu
          </button>
        </form>
      )}
    </AuthCard>
  )
}
