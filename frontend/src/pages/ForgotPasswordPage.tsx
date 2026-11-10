import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import { AuthCard } from '../components/AuthCard'
import { Alert, Field } from '../components/Form'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/api/auth/forgot-password', { method: 'POST', body: { email: email.trim() } })
      setSent(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title="Quên mật khẩu" footer={<Link to="/login">Quay lại đăng nhập</Link>}>
      {sent ? (
        <Alert kind="success">
          Nếu email này đã đăng ký, chúng tôi đã gửi liên kết đặt lại mật khẩu (hiệu lực 1 giờ). Môi trường dev:
          liên kết nằm trong log của backend.
        </Alert>
      ) : (
        <form onSubmit={submit} className="stack">
          <p className="muted">Nhập email của bạn, chúng tôi sẽ gửi liên kết để đặt lại mật khẩu.</p>
          <Field label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <Alert>{error}</Alert>
          <button type="submit" disabled={busy}>
            Gửi liên kết
          </button>
        </form>
      )}
    </AuthCard>
  )
}
