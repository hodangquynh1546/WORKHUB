import { useEffect, useRef } from 'react'
import { useRealtime } from './RealtimeContext'

/**
 * Calls `reload` (debounced) when someone changes one of `areas` of the project, and again when the connection
 * came back after a drop (changes may have been missed). Also keeps the project subscription while mounted.
 */
export function useLiveReload(projectId: string | null | undefined, areas: string[], reload: () => void) {
  const { on, subscribe } = useRealtime()
  const latest = useRef(reload)
  useEffect(() => {
    latest.current = reload
  })
  const key = areas.join(',')

  useEffect(() => {
    if (!projectId) return
    const wanted = new Set(key.split(','))
    let timer: ReturnType<typeof setTimeout> | undefined
    const schedule = () => {
      clearTimeout(timer)
      timer = setTimeout(() => latest.current(), 300)
    }
    const release = subscribe(projectId)
    const offChange = on('project.changed', (frame) => {
      if (frame.projectId === projectId && wanted.has(String(frame.area))) schedule()
    })
    const offReconnect = on('reconnected', schedule)
    return () => {
      clearTimeout(timer)
      release()
      offChange()
      offReconnect()
    }
  }, [projectId, key, on, subscribe])
}
