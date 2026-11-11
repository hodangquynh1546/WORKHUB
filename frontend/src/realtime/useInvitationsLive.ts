import { useEffect, useRef } from 'react'
import { useRealtime } from './RealtimeContext'

/**
 * Calls `reload` (debounced) when an invitation was sent, accepted, declined or revoked, when a workspace, team or
 * project was created, changed, archived or deleted (`structure.changed`), and after a reconnect.
 */
export function useInvitationsLive(reload: () => void) {
  const { on } = useRealtime()
  const latest = useRef(reload)
  useEffect(() => {
    latest.current = reload
  })
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    const schedule = () => {
      clearTimeout(timer)
      timer = setTimeout(() => latest.current(), 300)
    }
    const offChange = on('invitations.changed', schedule)
    const offStructure = on('structure.changed', schedule)
    const offReconnect = on('reconnected', schedule)
    return () => {
      clearTimeout(timer)
      offChange()
      offStructure()
      offReconnect()
    }
  }, [on])
}
