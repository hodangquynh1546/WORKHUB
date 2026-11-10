import { useState, type FormEvent } from 'react'
import { api, applySession } from '../api/client'
import { errorMessage } from '../api/errors'
import type { AuthResponse, UserProfile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Alert, Field } from '../components/Form'

type Message = { ok: boolean; text: string } | null

export function ProfilePage() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <>
      <h1>Hồ sơ của bạn</h1>
      <div className="two-column">
        <ProfileForm user={user} />
        <ChangePasswordForm />
      </div>
    </>
  )
}

function ProfileForm({ user }: { user: UserProfile }) {
  const { setUser } = useAuth()
  const [fullName, setFullName] = useState(user.fullName)
  const [avatarUrl, setAvatarUrl] = useState(user.avatarUrl ?? '')
  const [message, setMessage] = useState<Message>(null)
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      setUser(await api<UserProfile>('/api/me', { method: 'PUT', body: { fullName, avatarUrl: avatarUrl.trim() || null } }))
      setMessage({ ok: true, text: 'Đã lưu hồ sơ.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h2>Thông tin cá nhân</h2>
      <form onSubmit={save} className="stack">
        <Field label="Email" value={user.email} disabled />
        <p className="muted">{user.emailVerified ? '✓ Email đã được xác thực' : 'Email chưa được xác thực'}</p>
        <Field label="Họ tên" required maxLength={150} value={fullName} onChange={(e) => setFullName(e.target.value)} />
        <Field
          label="Ảnh đại diện URL (http/https)"
          type="url"
          maxLength={1024}
          placeholder="https://…"
          value={avatarUrl}
          onChange={(e) => setAvatarUrl(e.target.value)}
        />
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit" disabled={busy}>
          Lưu hồ sơ
        </button>
      </form>
    </section>
  )
}

function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [message, setMessage] = useState<Message>(null)
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent) {
    event.preventDefault()
    if (newPassword !== confirm) {
      setMessage({ ok: false, text: 'Mật khẩu nhập lại không khớp.' })
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      // The backend signs out other sessions and returns a fresh one for this browser.
      applySession(
        await api<AuthResponse>('/api/me/password', { method: 'POST', body: { currentPassword, newPassword } }),
      )
      setCurrentPassword('')
      setNewPassword('')
      setConfirm('')
      setMessage({ ok: true, text: 'Đã đổi mật khẩu. Các thiết bị khác đã bị đăng xuất.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h2>Đổi mật khẩu</h2>
      <form onSubmit={save} className="stack">
        <Field
          label="Mật khẩu hiện tại"
          type="password"
          autoComplete="current-password"
          required
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
        />
        <Field
          label="Mật khẩu mới (tối thiểu 8 ký tự)"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        <Field
          label="Nhập lại mật khẩu mới"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit" disabled={busy}>
          Đổi mật khẩu
        </button>
      </form>
    </section>
  )
}
