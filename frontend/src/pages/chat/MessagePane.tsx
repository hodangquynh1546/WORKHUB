import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ChatMessage, ChatMessagePage, ConversationSummary, UserRef } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useAuth } from '../../auth/AuthContext'
import { Alert } from '../../components/Form'
import { Avatar } from '../../components/Avatar'
import { useRealtime, type Frame } from '../../realtime/RealtimeContext'
import { extractMentions, mentionQuery } from './mentions'

const EMOJIS = ['👍', '❤️', '😄', '🎉', '🙏', '👀']
const PAGE = 40

const time = (iso: string) => new Date(iso).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
const dayLabel = (iso: string) => new Date(iso).toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })

/** Wraps `@Full Name` of mentioned people in a highlight. */
function withMentions(body: string, people: UserRef[]): ReactNode[] {
  const names = people.map((p) => p.fullName).filter(Boolean).sort((a, b) => b.length - a.length)
  if (names.length === 0) return [body]
  const pattern = new RegExp(`(@(?:${names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')}))`, 'gi')
  return body.split(pattern).map((part, i) => (i % 2 === 1 ? <mark key={i} className="mention">{part}</mark> : part))
}

export function MessagePane({ conversation, online, onBack, onChanged }: { conversation: ConversationSummary; online: boolean | null; onBack: () => void; onChanged: () => void }) {
  const { user } = useAuth()
  const { on } = useRealtime()
  const [messages, setMessages] = useState<ChatMessage[] | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { data: members } = useResource<UserRef[]>(`/api/chat/conversations/${conversation.id}/members`)
  const scroller = useRef<HTMLDivElement>(null)
  const stick = useRef(true)
  const restore = useRef<number | null>(null)
  const id = conversation.id

  const markRead = useCallback(() => {
    api(`/api/chat/conversations/${id}/read`, { method: 'POST' }).then(onChanged).catch(() => {})
  }, [id, onChanged])

  const loadLatest = useCallback(async () => {
    try {
      const page = await api<ChatMessagePage>(`/api/chat/conversations/${id}/messages?size=${PAGE}`)
      setMessages(page.items)
      setHasMore(page.hasMore)
      stick.current = true
      markRead()
    } catch (err) {
      setError(errorMessage(err))
    }
  }, [id, markRead])

  useEffect(() => {
    const timer = setTimeout(() => void loadLatest(), 0)
    return () => clearTimeout(timer)
  }, [loadLatest])

  // Live updates for this conversation.
  useEffect(() => {
    const mine = (frame: Frame) => frame.conversationId === id
    const offs = [
      on('chat.message', (frame) => {
        if (!mine(frame)) return
        const message = frame.message as ChatMessage
        setMessages((list) => (list && !list.some((m) => m.id === message.id) ? [...list, message] : list))
        if (message.sender?.id !== user?.id) markRead()
      }),
      on('chat.message.updated', (frame) => {
        if (!mine(frame)) return
        const message = frame.message as ChatMessage
        setMessages((list) => list?.map((m) => (m.id === message.id ? message : m)) ?? list)
      }),
      on('chat.message.deleted', (frame) => {
        if (!mine(frame)) return
        setMessages((list) => list?.map((m) => (m.id === frame.messageId ? { ...m, deleted: true, body: null, reactions: [], mentions: [] } : m)) ?? list)
      }),
      on('reconnected', () => void loadLatest()),
    ]
    return () => offs.forEach((off) => off())
  }, [on, id, user?.id, markRead, loadLatest])

  // Keep the view at the bottom for new messages; when older ones were prepended, keep what the reader was looking at.
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el) return
    if (restore.current != null) {
      el.scrollTop = el.scrollHeight - restore.current
      restore.current = null
    } else if (stick.current) el.scrollTop = el.scrollHeight
  }, [messages])

  async function loadOlder() {
    if (!messages?.length) return
    const el = scroller.current
    try {
      const page = await api<ChatMessagePage>(`/api/chat/conversations/${id}/messages?size=${PAGE}&before=${encodeURIComponent(messages[0].createdAt)}`)
      restore.current = el ? el.scrollHeight : null
      stick.current = false
      setMessages((list) => [...page.items, ...(list ?? [])])
      setHasMore(page.hasMore)
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null)
  const byId = useMemo(() => new Map((members ?? []).map((m) => [m.id, m])), [members])

  async function act(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const rows: ReactNode[] = []
  let lastDay = ''
  let lastSender = ''
  let lastAt = 0
  for (const m of messages ?? []) {
    const day = dayLabel(m.createdAt)
    if (day !== lastDay) {
      rows.push(<div key={`day-${m.id}`} className="msg-day">{day}</div>)
      lastDay = day
      lastSender = ''
    }
    const at = new Date(m.createdAt).getTime()
    const compact = m.sender?.id === lastSender && at - lastAt < 5 * 60_000 && !m.replyTo
    lastSender = m.sender?.id ?? ''
    lastAt = at
    rows.push(<Message key={m.id} message={m} compact={compact} selfId={user?.id} people={members ?? []} byId={byId} onReply={() => setReplyTo(m)} act={act} />)
  }

  return (
    <div className="msg-pane">
      <header className="msg-head">
        <button type="button" className="button-secondary button-small msg-back" onClick={onBack} aria-label="Quay lại danh sách">
          ‹
        </button>
        <div>
          <h2>{conversation.kind === 'PROJECT' ? '# ' : ''}{conversation.title}</h2>
          <p className="muted small">
            {conversation.kind === 'DIRECT' ? (online ? 'Đang trực tuyến' : 'Ngoại tuyến') : `${members?.length ?? conversation.memberCount} thành viên`}
          </p>
        </div>
      </header>
      <div
        className="msg-scroll"
        ref={scroller}
        onScroll={(e) => {
          const el = e.currentTarget
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
        }}
      >
        {hasMore && (
          <button type="button" className="button-secondary button-small msg-older" onClick={loadOlder}>
            Tải tin cũ hơn
          </button>
        )}
        {messages === null && !error && <p className="muted">Đang tải…</p>}
        {messages?.length === 0 && <p className="muted msg-empty">Chưa có tin nhắn nào. Hãy là người mở đầu.</p>}
        {rows}
      </div>
      <Alert>{error}</Alert>
      <Composer
        conversationId={id}
        people={members ?? []}
        selfId={user?.id}
        replyTo={replyTo}
        onCancelReply={() => setReplyTo(null)}
        onSent={() => {
          setReplyTo(null)
          stick.current = true
        }}
        onError={setError}
      />
    </div>
  )
}

function Message({ message: m, compact, selfId, people, byId, onReply, act }: { message: ChatMessage; compact: boolean; selfId?: string; people: UserRef[]; byId: Map<string, UserRef>; onReply: () => void; act: (action: () => Promise<unknown>) => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const own = m.sender?.id === selfId
  const mentioned = selfId ? m.mentions.includes(selfId) : false
  const mentionedPeople = m.mentions.map((id) => byId.get(id)).filter((p): p is UserRef => !!p)

  const react = (emoji: string) => act(() => api(`/api/chat/messages/${m.id}/reactions`, { method: 'POST', body: { emoji } }))

  return (
    <article className={`msg ${compact ? 'compact' : ''} ${mentioned ? 'msg-mentioned' : ''}`}>
      {compact ? <span className="msg-gutter muted small">{time(m.createdAt)}</span> : <Avatar name={m.sender?.fullName ?? '?'} url={m.sender?.avatarUrl} />}
      <div className="msg-body">
        {!compact && (
          <div className="msg-meta">
            <strong>{m.sender?.fullName ?? 'Người dùng đã rời'}</strong>
            <span className="muted small">{time(m.createdAt)}</span>
            {m.editedAt && !m.deleted && <span className="muted small">(đã sửa)</span>}
          </div>
        )}
        {m.replyTo && (
          <div className="msg-reply">
            <strong>{m.replyTo.senderName}</strong> {m.replyTo.deleted ? <em>Tin nhắn đã bị xóa</em> : m.replyTo.snippet}
          </div>
        )}
        {m.deleted ? (
          <p className="muted msg-deleted">Tin nhắn đã bị xóa</p>
        ) : editing ? (
          <form
            className="stack"
            onSubmit={async (e) => {
              e.preventDefault()
              await act(() => api(`/api/chat/messages/${m.id}`, { method: 'PUT', body: { body: draft } }))
              setEditing(false)
            }}
          >
            <textarea rows={2} value={draft} maxLength={4000} onChange={(e) => setDraft(e.target.value)} aria-label="Sửa tin nhắn" autoFocus />
            <div className="actions">
              <button type="submit" className="button-small" disabled={!draft.trim()}>
                Lưu
              </button>
              <button type="button" className="button-secondary button-small" onClick={() => setEditing(false)}>
                Hủy
              </button>
            </div>
          </form>
        ) : (
          <p className="prewrap msg-text">{withMentions(m.body ?? '', mentionedPeople.length ? mentionedPeople : people)}</p>
        )}
        {m.reactions.length > 0 && (
          <div className="msg-reactions">
            {m.reactions.map((r) => (
              <button key={r.emoji} type="button" className={`reaction ${selfId && r.userIds.includes(selfId) ? 'on' : ''}`} onClick={() => react(r.emoji)} aria-label={`${r.emoji} ${r.userIds.length}`}>
                {r.emoji} {r.userIds.length}
              </button>
            ))}
          </div>
        )}
      </div>
      {!m.deleted && !editing && (
        <div className="msg-actions" role="toolbar" aria-label="Thao tác tin nhắn">
          {EMOJIS.map((emoji) => (
            <button key={emoji} type="button" className="msg-act" onClick={() => react(emoji)} aria-label={`Thả ${emoji}`}>
              {emoji}
            </button>
          ))}
          <button type="button" className="msg-act msg-act-text" onClick={onReply}>
            Trả lời
          </button>
          {own && (
            <>
              <button type="button" className="msg-act msg-act-text" onClick={() => (setDraft(m.body ?? ''), setEditing(true))}>
                Sửa
              </button>
              <button type="button" className="msg-act msg-act-text" onClick={() => window.confirm('Xóa tin nhắn này?') && act(() => api(`/api/chat/messages/${m.id}`, { method: 'DELETE' }))}>
                Xóa
              </button>
            </>
          )}
        </div>
      )}
    </article>
  )
}

function Composer({ conversationId, people, selfId, replyTo, onCancelReply, onSent, onError }: { conversationId: string; people: UserRef[]; selfId?: string; replyTo: ChatMessage | null; onCancelReply: () => void; onSent: () => void; onError: (message: string | null) => void }) {
  const [text, setText] = useState('')
  const [caret, setCaret] = useState(0)
  const [busy, setBusy] = useState(false)
  const field = useRef<HTMLTextAreaElement>(null)
  const query = mentionQuery(text, caret)
  const suggestions = query ? people.filter((p) => p.id !== selfId && p.fullName.toLowerCase().includes(query.query)).slice(0, 5) : []
  const [choice, setChoice] = useState(0)

  function insertMention(person: UserRef) {
    if (!query) return
    const next = `${text.slice(0, query.start)}@${person.fullName} ${text.slice(caret)}`
    const position = query.start + person.fullName.length + 2
    setText(next)
    setCaret(position)
    requestAnimationFrame(() => {
      field.current?.focus()
      field.current?.setSelectionRange(position, position)
    })
  }

  async function send() {
    const body = text.trim()
    if (!body || busy) return
    setBusy(true)
    onError(null)
    try {
      await api(`/api/chat/conversations/${conversationId}/messages`, {
        method: 'POST',
        body: { body, replyToId: replyTo?.id ?? null, mentionIds: extractMentions(body, people, selfId) },
      })
      setText('')
      setCaret(0)
      onSent()
    } catch (err) {
      onError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (suggestions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setChoice((c) => (c + 1) % suggestions.length)
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        setChoice((c) => (c - 1 + suggestions.length) % suggestions.length)
      } else if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        insertMention(suggestions[choice % suggestions.length])
      }
      return
    }
    // Enter sends, Shift+Enter makes a new line.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void send()
    }
  }

  return (
    <div className="composer">
      {replyTo && (
        <div className="composer-reply">
          <span className="small">
            Đang trả lời <strong>{replyTo.sender?.fullName}</strong>: {replyTo.body?.slice(0, 80)}
          </span>
          <button type="button" className="link-button small" onClick={onCancelReply}>
            Hủy
          </button>
        </div>
      )}
      {suggestions.length > 0 && (
        <ul className="mention-list" role="listbox" aria-label="Gợi ý nhắc đến">
          {suggestions.map((p, i) => (
            <li key={p.id}>
              <button type="button" role="option" aria-selected={i === choice % suggestions.length} className={i === choice % suggestions.length ? 'on' : ''} onMouseDown={(e) => (e.preventDefault(), insertMention(p))}>
                <Avatar name={p.fullName} url={p.avatarUrl} /> {p.fullName}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="composer-row">
        <textarea
          ref={field}
          rows={2}
          value={text}
          maxLength={4000}
          placeholder="Nhập tin nhắn… (@ để nhắc đến, Enter để gửi, Shift+Enter xuống dòng)"
          aria-label="Tin nhắn"
          onChange={(e) => (setText(e.target.value), setCaret(e.target.selectionStart), setChoice(0))}
          onKeyDown={onKeyDown}
          onKeyUp={(e) => setCaret(e.currentTarget.selectionStart)}
          onClick={(e) => setCaret(e.currentTarget.selectionStart)}
        />
        <button type="button" onClick={send} disabled={busy || !text.trim()}>
          Gửi
        </button>
      </div>
    </div>
  )
}
