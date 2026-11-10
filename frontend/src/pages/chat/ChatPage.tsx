import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { ChatSearchHit, ConversationKind, ConversationSummary, Workspace, WorkspaceMember } from '../../api/types'
import { useResource } from '../../api/useResource'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Field } from '../../components/Form'
import { Avatar } from '../../components/Avatar'
import { ListSkeleton } from '../../components/Skeleton'
import { useRealtime } from '../../realtime/RealtimeContext'
import { timeAgo } from '../notificationLabels'
import { MessagePane } from './MessagePane'

const SECTIONS: { kind: ConversationKind; title: string }[] = [
  { kind: 'PROJECT', title: 'Dự án' },
  { kind: 'TEAM', title: 'Team' },
  { kind: 'GROUP', title: 'Nhóm' },
  { kind: 'DIRECT', title: 'Tin nhắn trực tiếp' },
]

export function ChatPage() {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const { on } = useRealtime()
  const { data, error, reload } = useResource<ConversationSummary[]>('/api/chat/conversations')
  const [creating, setCreating] = useState<'direct' | 'group' | null>(null)
  const [online, setOnline] = useState<Set<string>>(new Set())

  // The list (last message, unread badges, order) follows the conversation traffic.
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    const refresh = () => {
      clearTimeout(timer)
      timer = setTimeout(reload, 300)
    }
    const offs = [on('chat.message', refresh), on('chat.message.deleted', refresh), on('chat.read', refresh), on('reconnected', refresh)]
    return () => {
      clearTimeout(timer)
      offs.forEach((off) => off())
    }
  }, [on, reload])

  const others = useMemo(() => (data ?? []).flatMap((c) => (c.other ? [c.other.id] : [])).join(','), [data])
  useEffect(() => {
    if (!others) return
    let cancelled = false
    const poll = () =>
      api<{ online: string[] }>(`/api/presence?userIds=${others}`)
        .then((r) => !cancelled && setOnline(new Set(r.online)))
        .catch(() => {})
    void poll()
    const timer = setInterval(poll, 30_000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [others])

  const selected = data?.find((c) => c.id === conversationId) ?? null

  return (
    <div className={`chat ${conversationId ? 'chat-open' : ''}`}>
      <aside className="chat-side card">
        <div className="chat-side-head">
          <h1>Trò chuyện</h1>
          <div className="actions">
            <button type="button" className="button-secondary button-small" onClick={() => setCreating(creating === 'direct' ? null : 'direct')}>
              Tin nhắn mới
            </button>
            <button type="button" className="button-secondary button-small" onClick={() => setCreating(creating === 'group' ? null : 'group')}>
              Tạo nhóm
            </button>
          </div>
        </div>
        {creating && (
          <NewConversation
            mode={creating}
            onDone={(id) => {
              setCreating(null)
              reload()
              navigate(`/chat/${id}`)
            }}
            onCancel={() => setCreating(null)}
          />
        )}
        <Search onOpen={(id) => navigate(`/chat/${id}`)} />
        <Alert>{error}</Alert>
        {!data && !error && <ListSkeleton rows={5} />}
        {data?.length === 0 && <p className="muted small">Bạn chưa có cuộc trò chuyện nào. Kênh của dự án và team xuất hiện khi bạn là thành viên.</p>}
        <nav aria-label="Cuộc trò chuyện" className="chat-list">
          {SECTIONS.map((section) => {
            const items = data?.filter((c) => c.kind === section.kind) ?? []
            if (items.length === 0) return null
            return (
              <div key={section.kind}>
                <h3 className="chat-section">{section.title}</h3>
                {items.map((c) => (
                  <Link key={c.id} to={`/chat/${c.id}`} className={`chat-item ${c.id === conversationId ? 'on' : ''}`}>
                    {c.other ? (
                      <span className="presence-wrap">
                        <Avatar name={c.other.fullName} url={c.other.avatarUrl} />
                        <span className={`presence ${online.has(c.other.id) ? 'online' : ''}`} title={online.has(c.other.id) ? 'Đang trực tuyến' : 'Ngoại tuyến'} />
                      </span>
                    ) : (
                      <span className="chat-hash" aria-hidden="true">
                        {c.kind === 'PROJECT' ? '#' : c.kind === 'TEAM' ? '◆' : '●'}
                      </span>
                    )}
                    <span className="chat-item-main">
                      <span className="chat-item-title">{c.title}</span>
                      <span className="chat-item-last muted small">{c.last ? `${c.last.senderName ? `${c.last.senderName}: ` : ''}${c.last.snippet}` : 'Chưa có tin nhắn'}</span>
                    </span>
                    <span className="chat-item-meta">
                      {c.lastMessageAt && <span className="muted small">{timeAgo(c.lastMessageAt)}</span>}
                      {c.unread > 0 && <span className="badge-count">{c.unread > 99 ? '99+' : c.unread}</span>}
                    </span>
                  </Link>
                ))}
              </div>
            )
          })}
        </nav>
      </aside>

      <section className="chat-main card">
        {conversationId && selected ? (
          <MessagePane key={selected.id} conversation={selected} online={selected.other ? online.has(selected.other.id) : null} onBack={() => navigate('/chat')} onChanged={reload} />
        ) : conversationId && data ? (
          <div className="empty-state">
            <h2>Không tìm thấy cuộc trò chuyện</h2>
            <p className="muted">Nó không tồn tại hoặc bạn không còn trong đó.</p>
          </div>
        ) : (
          <div className="empty-state">
            <h2>Chọn một cuộc trò chuyện</h2>
            <p className="muted">Kênh dự án và team ở bên trái. Bấm "Tin nhắn mới" để nhắn riêng cho một đồng nghiệp.</p>
          </div>
        )}
      </section>
    </div>
  )
}

function Search({ onOpen }: { onOpen: (conversationId: string) => void }) {
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<ChatSearchHit[] | null>(null)

  useEffect(() => {
    const term = q.trim()
    if (term.length < 2) {
      const reset = setTimeout(() => setHits(null), 0)
      return () => clearTimeout(reset)
    }
    let cancelled = false
    const timer = setTimeout(() => {
      api<ChatSearchHit[]>(`/api/chat/search?q=${encodeURIComponent(term)}`)
        .then((r) => !cancelled && setHits(r))
        .catch(() => !cancelled && setHits([]))
    }, 350)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [q])

  return (
    <div className="chat-search">
      <input type="search" placeholder="Tìm trong tin nhắn…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Tìm tin nhắn" />
      {hits && (
        <ul className="plain-list chat-hits">
          {hits.length === 0 && <li className="muted small">Không tìm thấy tin nhắn nào.</li>}
          {hits.map((h) => (
            <li key={h.message.id}>
              <button type="button" className="chat-hit" onClick={() => (setQ(''), onOpen(h.message.conversationId))}>
                <span className="small muted">
                  {h.conversationTitle} · {h.message.sender?.fullName} · {timeAgo(h.message.createdAt)}
                </span>
                <span>{h.message.body}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function NewConversation({ mode, onDone, onCancel }: { mode: 'direct' | 'group'; onDone: (conversationId: string) => void; onCancel: () => void }) {
  const { data: workspaces } = useResource<Workspace[]>('/api/workspaces')
  const [workspaceId, setWorkspaceId] = useState('')
  const wsId = workspaceId || workspaces?.[0]?.id || ''

  return (
    <div className="stack chat-new">
      <label className="field">
        <span>Workspace</span>
        <select value={wsId} onChange={(e) => setWorkspaceId(e.target.value)}>
          {workspaces?.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </select>
      </label>
      {wsId && <PeoplePicker key={wsId} mode={mode} workspaceId={wsId} onDone={onDone} onCancel={onCancel} />}
    </div>
  )
}

function PeoplePicker({ mode, workspaceId, onDone, onCancel }: { mode: 'direct' | 'group'; workspaceId: string; onDone: (conversationId: string) => void; onCancel: () => void }) {
  const { user } = useAuth()
  const { data: members } = useResource<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`)
  const [title, setTitle] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const people = (members ?? []).filter((m) => m.userId !== user?.id)

  const start = useCallback(
    async (path: string, body: object) => {
      setError(null)
      try {
        onDone((await api<ConversationSummary>(path, { method: 'POST', body })).id)
      } catch (err) {
        setError(errorMessage(err))
      }
    },
    [onDone],
  )

  function submit(event: FormEvent) {
    event.preventDefault()
    void start('/api/chat/groups', { workspaceId, title, memberIds: picked })
  }

  return (
    <form className="stack" onSubmit={submit}>
      {mode === 'group' && <Field label="Tên nhóm" required maxLength={100} value={title} onChange={(e) => setTitle(e.target.value)} />}
      <ul className="plain-list picker-list">
        {people.map((m) => (
          <li key={m.userId} className="row">
            {mode === 'group' ? (
              <label className="inline-check attendee-option">
                <input type="checkbox" checked={picked.includes(m.userId)} onChange={(e) => setPicked(e.target.checked ? [...picked, m.userId] : picked.filter((id) => id !== m.userId))} />
                {m.fullName}
              </label>
            ) : (
              <>
                <span className="workload-person">
                  <Avatar name={m.fullName} url={m.avatarUrl} />
                  {m.fullName}
                </span>
                <button type="button" className="button-small" onClick={() => start('/api/chat/direct', { workspaceId, userId: m.userId })}>
                  Nhắn tin
                </button>
              </>
            )}
          </li>
        ))}
        {people.length === 0 && <li className="muted small">Workspace này chưa có ai khác.</li>}
      </ul>
      <Alert>{error}</Alert>
      <div className="actions">
        {mode === 'group' && (
          <button type="submit" disabled={!title.trim() || picked.length === 0}>
            Tạo nhóm
          </button>
        )}
        <button type="button" className="button-secondary" onClick={onCancel}>
          Đóng
        </button>
      </div>
    </form>
  )
}
