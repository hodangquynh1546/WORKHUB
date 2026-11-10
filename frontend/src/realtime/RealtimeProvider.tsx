import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { currentAccessToken, refreshSession } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { RealtimeContext, type Frame, type RealtimeApi, type RealtimeStatus } from './RealtimeContext'

const PING_MS = 25_000
const MAX_BACKOFF_MS = 30_000

/**
 * One WebSocket per signed-in browser tab. It authenticates with its first frame (never in the URL), keeps itself
 * alive with pings, reconnects with growing delays (fetching a fresh access token first), re-subscribes to the
 * projects pages asked for, and tells listeners when it came back so they can reload what they missed.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const userId = user?.id ?? null
  const [status, setStatus] = useState<RealtimeStatus>('closed')
  const listeners = useRef(new Map<string, Set<(frame: Frame) => void>>())
  const projects = useRef(new Map<string, number>())
  const socket = useRef<WebSocket | null>(null)

  const emit = useCallback((type: string, frame: Frame) => {
    listeners.current.get(type)?.forEach((fn) => fn(frame))
  }, [])

  const send = useCallback((payload: object) => {
    if (socket.current?.readyState === WebSocket.OPEN) socket.current.send(JSON.stringify(payload))
  }, [])

  useEffect(() => {
    if (!userId) return
    let stopped = false
    let attempt = 0
    let retry: ReturnType<typeof setTimeout> | undefined
    let ping: ReturnType<typeof setInterval> | undefined
    let everConnected = false

    async function connect() {
      if (stopped) return
      setStatus('connecting')
      // A token that is about to expire would be rejected on the first frame; get a fresh one when unsure.
      const token = currentAccessToken() ?? (await refreshSession())?.accessToken ?? null
      if (stopped || !token) return
      const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
      const ws = new WebSocket(`${protocol}://${location.host}/ws`)
      socket.current = ws
      let ready = false

      ws.onopen = () => ws.send(JSON.stringify({ type: 'auth', token }))
      ws.onmessage = (event) => {
        let frame: Frame
        try {
          frame = JSON.parse(event.data as string)
        } catch {
          return
        }
        if (frame.type === 'ready') {
          ready = true
          attempt = 0
          setStatus('open')
          projects.current.forEach((_, id) => ws.send(JSON.stringify({ type: 'subscribe', projectId: id })))
          ping = setInterval(() => ws.send(JSON.stringify({ type: 'ping' })), PING_MS)
          if (everConnected) emit('reconnected', frame)
          everConnected = true
        }
        emit(frame.type, frame)
      }
      ws.onclose = async () => {
        clearInterval(ping)
        if (socket.current === ws) socket.current = null
        if (stopped) return
        setStatus('connecting')
        // Closed before it was ready usually means the token was refused: get a new one next time.
        if (!ready) await refreshSession()
        const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** attempt++)
        retry = setTimeout(connect, delay)
      }
    }

    void connect()
    return () => {
      stopped = true
      clearTimeout(retry)
      clearInterval(ping)
      socket.current?.close()
      socket.current = null
      setStatus('closed')
    }
  }, [userId, emit])

  const api = useMemo<RealtimeApi>(
    () => ({
      status,
      on(type, fn) {
        const set = listeners.current.get(type) ?? new Set()
        set.add(fn)
        listeners.current.set(type, set)
        return () => void set.delete(fn)
      },
      subscribe(projectId) {
        const count = projects.current.get(projectId) ?? 0
        projects.current.set(projectId, count + 1)
        if (count === 0) send({ type: 'subscribe', projectId })
        return () => {
          const left = (projects.current.get(projectId) ?? 1) - 1
          if (left <= 0) {
            projects.current.delete(projectId)
            send({ type: 'unsubscribe', projectId })
          } else projects.current.set(projectId, left)
        }
      },
    }),
    [status, send],
  )

  return <RealtimeContext.Provider value={api}>{children}</RealtimeContext.Provider>
}
