import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Invitation, InvitationAccepted } from '../api/types'
import { useResource } from '../api/useResource'
import { useAuth } from '../auth/AuthContext'
import { AuthCard } from '../components/AuthCard'
import { Alert } from '../components/Form'
import { INVITATION_STATUS_TEXT, invitationRoleLabel } from './roles'

/** Landing page of an invitation link; works signed out (preview) and signed in (accept/decline). */
export function InvitationPage() {
  const { token } = useParams()
  const navigate = useNavigate()
  const { ready, user } = useAuth()
  const { data: invitation, error } = useResource<Invitation>(`/api/public/invitations/${token}`)
  const [actionError, setActionError] = useState<string | null>(null)
  const [declined, setDeclined] = useState(false)
  const [busy, setBusy] = useState(false)
  const back = { from: `/invitations/${token}` }

  async function accept() {
    setBusy(true)
    setActionError(null)
    try {
      const result = await api<InvitationAccepted>('/api/invitations/accept', { method: 'POST', body: { token } })
      navigate(result.projectId ? `/projects/${result.projectId}` : result.teamId ? `/teams/${result.teamId}` : `/workspaces/${result.workspaceId}`)
    } catch (err) {
      setActionError(errorMessage(err))
      setBusy(false)
    }
  }

  async function decline() {
    setBusy(true)
    setActionError(null)
    try {
      await api('/api/invitations/decline', { method: 'POST', body: { token } })
      setDeclined(true)
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return (
      <AuthCard title="Lời mời" footer={<Link to="/workspaces">Về trang chủ</Link>}>
        <Alert>{error}</Alert>
      </AuthCard>
    )
  }
  if (!invitation || !ready) {
    return (
      <AuthCard title="Lời mời">
        <p className="muted">Đang tải…</p>
      </AuthCard>
    )
  }

  const target = invitation.projectName ? `dự án ${invitation.projectName} (workspace ${invitation.workspaceName})` : invitation.teamName ? `team ${invitation.teamName} (workspace ${invitation.workspaceName})` : `workspace ${invitation.workspaceName}`
  const wrongAccount = invitation.kind === 'EMAIL' && user && user.email !== invitation.email

  return (
    <AuthCard title="Bạn được mời tham gia" footer={<Link to="/workspaces">Về trang chủ</Link>}>
      <div className="stack">
        <p>
          <strong>{invitation.invitedByName}</strong> mời bạn tham gia <strong>{target}</strong> với vai trò{' '}
          <strong>{invitationRoleLabel(invitation.teamId, invitation.role, invitation.projectId)}</strong>.
        </p>
        {invitation.status !== 'PENDING' ? (
          <Alert>{INVITATION_STATUS_TEXT[invitation.status]}</Alert>
        ) : declined ? (
          <Alert kind="info">Bạn đã từ chối lời mời.</Alert>
        ) : !user ? (
          <>
            <p className="muted">
              {invitation.kind === 'EMAIL'
                ? `Lời mời gửi tới ${invitation.email}. Hãy đăng nhập hoặc đăng ký bằng email này.`
                : 'Hãy đăng nhập hoặc đăng ký để tham gia.'}
            </p>
            <div className="actions">
              <Link className="button-link" to="/login" state={back}>
                Đăng nhập
              </Link>
              <Link className="button-link button-link-secondary" to="/register" state={back}>
                Đăng ký
              </Link>
            </div>
          </>
        ) : wrongAccount ? (
          <Alert>
            Lời mời gửi tới {invitation.email}, nhưng bạn đang đăng nhập bằng {user.email}. Hãy đăng xuất và đăng nhập
            đúng tài khoản.
          </Alert>
        ) : (
          <div className="actions">
            <button type="button" onClick={accept} disabled={busy}>
              Chấp nhận
            </button>
            {invitation.kind === 'EMAIL' && (
              <button type="button" className="button-secondary" onClick={decline} disabled={busy}>
                Từ chối
              </button>
            )}
          </div>
        )}
        <Alert>{actionError}</Alert>
      </div>
    </AuthCard>
  )
}
