import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ConversationSummary } from '../../api/types'
import { Alert } from '../../components/Form'
import { PageSkeleton } from '../../components/Skeleton'
import { useProject } from '../project/projectContext'

/** The project's "Trao đổi" tab: makes sure the project channel exists and opens it in the chat page. */
export function ProjectChatRedirect() {
  const { project } = useProject()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api<ConversationSummary>(`/api/chat/projects/${project.id}`, { method: 'POST' })
      .then((c) => !cancelled && navigate(`/chat/${c.id}`, { replace: true }))
      .catch((err) => !cancelled && setError(errorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [project.id, navigate])

  return error ? <Alert>{error}</Alert> : <PageSkeleton />
}
