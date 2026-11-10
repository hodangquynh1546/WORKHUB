import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import { useRealtime } from './RealtimeContext'

/** Unread counts for the top bar: notifications and chat messages, kept fresh by events (and re-read after a reconnect). */
export function useUnread() {
  const { on } = useRealtime()
  const [notifications, setNotifications] = useState(0)
  const [chat, setChat] = useState(0)

  const loadNotifications = useCallback(
    () => api<{ unread: number }>('/api/notifications/unread-count').then((r) => setNotifications(r.unread)).catch(() => {}),
    [],
  )
  const loadChat = useCallback(() => api<{ unread: number }>('/api/chat/unread-count').then((r) => setChat(r.unread)).catch(() => {}), [])

  useEffect(() => {
    void loadNotifications()
    void loadChat()
    let timer: ReturnType<typeof setTimeout> | undefined
    const refreshChat = () => {
      clearTimeout(timer)
      timer = setTimeout(() => void loadChat(), 250)
    }
    const offs = [
      on('notification', (frame) => setNotifications(Number(frame.unread ?? 0))),
      on('chat.message', refreshChat),
      on('chat.read', (frame) => setChat(Number(frame.unread ?? 0))),
      on('reconnected', () => {
        void loadNotifications()
        void loadChat()
      }),
    ]
    return () => {
      clearTimeout(timer)
      offs.forEach((off) => off())
    }
  }, [on, loadNotifications, loadChat])

  return { notifications, chat, setNotifications, reloadNotifications: loadNotifications }
}
