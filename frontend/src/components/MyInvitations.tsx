import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api/client'
import { errorMessage } from '../api/errors'
import type { Invitation, InvitationAccepted } from '../api/types'
import { useResource } from '../api/useResource'
import { invitationRoleLabel } from '../pages/roles'
import { Alert } from './Form'

/** Email invitations addressed to the signed-in user. Renders nothing when there are none. */
export function MyInvitations() {
  const navigate = useNavigate()
  const { data, reload } = useResource<Invitation[]>('/api/me/invitations')
  const [error, setError] = useState<string | null>(null)

  async function respond(invitation: Invitation, accept: boolean) {
    setError(null)
    try {
      if (accept) {
        const result = await api<InvitationAccepted>(`/api/me/invitations/${invitation.id}/accept`, { method: 'POST' })
        navigate(result.teamId ? `/teams/${result.teamId}` : `/workspaces/${result.workspaceId}`)
      } else {
        await api(`/api/me/invitations/${invitation.id}/decline`, { method: 'POST' })
        reload()
      }
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (!data?.length) return null
  return (
    <section className="card invitations-inbox">
      <h2>Lời mời dành cho bạn</h2>
      <Alert>{error}</Alert>
      <ul className="plain-list">
        {data.map((inv) => (
          <li key={inv.id} className="row">
            <div>
              <strong>{inv.teamName ? `Team ${inv.teamName} · ${inv.workspaceName}` : inv.workspaceName}</strong>
              <p className="muted small">
                {inv.invitedByName} mời bạn làm {invitationRoleLabel(inv.teamId, inv.role).toLowerCase()}
              </p>
            </div>
            <div className="actions">
              <button type="button" onClick={() => respond(inv, true)}>
                Chấp nhận
              </button>
              <button type="button" className="button-secondary" onClick={() => respond(inv, false)}>
                Từ chối
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
