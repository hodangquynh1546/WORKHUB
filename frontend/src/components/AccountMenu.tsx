import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api, applySession } from '../api/client'
import { errorMessage } from '../api/errors'
import { resizeToSquare } from '../api/resizeImage'
import type { AuthResponse, UserProfile } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from './Avatar'
import { Alert, Field } from './Form'

type Message = { ok: boolean; text: string } | null

/** The avatar button in the top bar; opens a panel to edit the account, change the password or sign out. */
export function AccountMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!user) return null

  return (
    <div className="account" ref={root}>
      <button
        type="button"
        className="account-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label="Tài khoản"
        onClick={() => setOpen((v) => !v)}
      >
        <Avatar name={user.fullName} url={user.avatarUrl} />
        <span className="topbar-name">{user.fullName}</span>
      </button>
      {open && (
        <section className="account-panel" role="dialog" aria-label="Tài khoản của bạn">
          <ProfileEditor user={user} />
          <details className="account-section">
            <summary>Đổi mật khẩu</summary>
            <ChangePasswordForm />
          </details>
          <button
            type="button"
            className="button-secondary account-logout"
            onClick={async () => {
              await logout()
              navigate('/login')
            }}
          >
            Đăng xuất
          </button>
        </section>
      )}
    </div>
  )
}

function ProfileEditor({ user }: { user: UserProfile }) {
  const { setUser } = useAuth()
  const [fullName, setFullName] = useState(user.fullName)
  const [message, setMessage] = useState<Message>(null)
  const [busy, setBusy] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  async function run(action: () => Promise<UserProfile>, success: string) {
    setBusy(true)
    setMessage(null)
    try {
      setUser(await action())
      setMessage({ ok: true, text: success })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  async function pickPhoto(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setMessage({ ok: false, text: 'Hãy chọn một tệp ảnh.' })
      return
    }
    await run(async () => {
      const form = new FormData()
      form.append('file', await resizeToSquare(file), 'avatar.jpg')
      return api<UserProfile>('/api/me/avatar', { method: 'PUT', body: form })
    }, 'Đã cập nhật ảnh đại diện.')
  }

  function save(event: FormEvent) {
    event.preventDefault()
    void run(() => api<UserProfile>('/api/me', { method: 'PUT', body: { fullName } }), 'Đã lưu.')
  }

  return (
    <div className="account-profile">
      <div className="account-identity">
        <button
          type="button"
          className="avatar-edit"
          aria-label="Chọn ảnh đại diện từ máy"
          disabled={busy}
          onClick={() => fileInput.current?.click()}
        >
          <Avatar name={user.fullName} url={user.avatarUrl} size="lg" />
          <span className="avatar-edit-hint">Đổi ảnh</span>
        </button>
        <div>
          <strong>{user.fullName}</strong>
          <p className="muted small">{user.email}</p>
          <p className="muted small">{user.emailVerified ? 'Email đã xác thực' : 'Email chưa xác thực'}</p>
        </div>
        <input
          ref={fileInput}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          hidden
          onChange={(e) => {
            void pickPhoto(e.target.files?.[0])
            e.target.value = ''
          }}
        />
      </div>
      <div className="actions">
        <button type="button" className="button-secondary button-small" disabled={busy} onClick={() => fileInput.current?.click()}>
          Chọn ảnh từ máy
        </button>
        {user.avatarUrl && (
          <button
            type="button"
            className="button-secondary button-small"
            disabled={busy}
            onClick={() => run(() => api<UserProfile>('/api/me/avatar', { method: 'DELETE' }), 'Đã xóa ảnh đại diện.')}
          >
            Xóa ảnh
          </button>
        )}
      </div>
      <form onSubmit={save} className="stack">
        <Field label="Họ tên" required maxLength={150} value={fullName} onChange={(e) => setFullName(e.target.value)} />
        {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
        <button type="submit" disabled={busy || !fullName.trim() || fullName.trim() === user.fullName}>
          Lưu thay đổi
        </button>
      </form>
    </div>
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
  )
}
