import { markdown } from '@codemirror/lang-markdown'
import { defaultKeymap } from '@codemirror/commands'
import { EditorState } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { useEffect, useRef, useState } from 'react'
import { yCollab, yUndoManagerKeymap } from 'y-codemirror.next'
import * as Y from 'yjs'
import { api } from '../../../api/client'
import { errorMessage } from '../../../api/errors'
import type { DocumentDetail, DocumentFolder, DocumentType } from '../../../api/types'
import { useAuth } from '../../../auth/AuthContext'
import { Alert } from '../../../components/Form'
import { useRealtime } from '../../../realtime/RealtimeContext'
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS } from '../labels'
import { CollabSession, type CollabStatus } from './CollabSession'

/** Cursors and selections need colours that tell people apart; everything else stays black and white. */
const COLORS = ['#2563eb', '#db2777', '#059669', '#d97706', '#7c3aed', '#dc2626', '#0891b2', '#65a30d']
const colorFor = (id: string) => COLORS[[...id].reduce((sum, c) => sum + c.charCodeAt(0), 0) % COLORS.length]

const STATUS: Record<CollabStatus, string> = {
  connecting: 'Đang kết nối…',
  syncing: 'Đang đồng bộ…',
  ready: 'Đã kết nối: thay đổi được chia sẻ ngay',
  offline: 'Mất kết nối: thay đổi được giữ trên máy và đồng bộ khi nối lại',
  reset: 'Tài liệu vừa được thay thế, đang tải lại…',
  error: 'Không đồng bộ được',
}

const theme = EditorView.theme({
  '&': { backgroundColor: 'var(--surface)', color: 'var(--text)', border: '1px solid var(--border-strong)', borderRadius: 'var(--radius-field)' },
  '&.cm-focused': { outline: '2px solid var(--ink)', outlineOffset: '0' },
  '.cm-scroller': { fontFamily: "ui-monospace, 'SF Mono', Consolas, monospace", fontSize: '0.9rem', lineHeight: '1.65', minHeight: '440px', maxHeight: '70vh' },
  '.cm-content': { padding: '0.75rem 0.5rem', caretColor: 'var(--text)' },
  '.cm-cursor': { borderLeftColor: 'var(--text)' },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground': { backgroundColor: 'var(--ink-soft) !important' },
})

type Person = { id: string; name: string; color: string; self: boolean }

/**
 * Live editing of one document by several people: a CodeMirror editor bound to a Yjs text, other people's cursors
 * with their names, and a presence list. Title, type and folder are saved separately; the text is saved by the
 * session itself a few seconds after each change, and added to the version history on request or when closing.
 */
export default function CollabEditor({ doc, folders, onDone, onReload }: { doc: DocumentDetail; folders: DocumentFolder[]; onDone: () => void; onReload: () => Promise<void> | void }) {
  const realtime = useRealtime()
  const { user } = useAuth()
  const latest = useRef(realtime)
  useEffect(() => {
    latest.current = realtime
  })
  const host = useRef<HTMLDivElement>(null)
  const [session, setSession] = useState<CollabSession | null>(null)
  const [, setTick] = useState(0)
  const [generation, setGeneration] = useState(0)
  const [note, setNote] = useState('')
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)
  const [meta, setMeta] = useState({ title: doc.title, type: doc.type as DocumentType, folderId: doc.folderId ?? '' })
  const bump = () => setTick((t) => t + 1)

  // One session per generation; a reset (restore, save from elsewhere) starts a fresh one from the reloaded document.
  useEffect(() => {
    if (!user) return
    const s = new CollabSession(doc.id, () => latest.current, bump, () => {
      void Promise.resolve(onReload()).then(() => setGeneration((g) => g + 1))
    })
    s.awareness.on('change', bump)
    const starting = setTimeout(() => {
      setSession(s)
      void s.start({ id: user.id, name: user.fullName, color: colorFor(user.id) })
    }, 0)
    return () => {
      clearTimeout(starting)
      setSession(null)
      // Stop reacting at once; store what is left, then free the document.
      s.detach()
      void s.flush().catch(() => {}).finally(() => s.destroy())
    }
    // The document id never changes while the editor is open; `generation` restarts the session after a reset.
  }, [doc.id, user, generation]) // eslint-disable-line react-hooks/exhaustive-deps

  // Build the editor once the session has the document (also when it is read-only).
  const ready = session && session.status !== 'connecting'
  useEffect(() => {
    if (!session || !ready || !host.current) return
    const undoManager = new Y.UndoManager(session.text)
    const view = new EditorView({
      parent: host.current,
      state: EditorState.create({
        doc: session.text.toString(),
        extensions: [
          keymap.of([...yUndoManagerKeymap, ...defaultKeymap]),
          markdown(),
          EditorView.lineWrapping,
          yCollab(session.text, session.awareness, { undoManager }),
          EditorState.readOnly.of(!session.canEdit),
          EditorView.editable.of(session.canEdit),
          theme,
        ],
      }),
    })
    return () => {
      view.destroy()
      undoManager.destroy()
    }
  }, [session, ready])

  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => session?.unsaved && e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [session])

  const people: Person[] = []
  if (session) {
    const seen = new Set<string>()
    session.awareness.getStates().forEach((state, clientId) => {
      const u = state.user as { id: string; name: string; color: string } | undefined
      if (!u || seen.has(u.id)) return
      seen.add(u.id)
      people.push({ id: u.id, name: u.name, color: u.color, self: clientId === session.doc.clientID })
    })
  }

  async function saveMeta() {
    setMessage(null)
    try {
      await api(`/api/documents/${doc.id}/collab/meta`, { method: 'PUT', body: { title: meta.title, type: meta.type, folderId: meta.folderId || null } })
      await onReload()
      setMessage({ ok: true, text: 'Đã lưu thông tin.' })
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    }
  }

  async function saveVersion() {
    if (!session) return
    setMessage(null)
    try {
      const result = await session.saveVersion(note.trim() || null)
      setNote('')
      setMessage({ ok: true, text: `Đã lưu vào lịch sử (phiên bản ${result.version}).` })
      await onReload()
    } catch (err) {
      setMessage({ ok: false, text: errorMessage(err) })
    }
  }

  async function finish() {
    if (session) {
      try {
        await session.flush()
      } catch (err) {
        setMessage({ ok: false, text: errorMessage(err) })
        return
      }
    }
    onDone()
  }

  const status = session?.status ?? 'connecting'
  return (
    <section className="card stack">
      <div className="inline-form editor-meta">
        <label className="field editor-title">
          <span>Tiêu đề</span>
          <input value={meta.title} maxLength={200} onChange={(e) => setMeta({ ...meta, title: e.target.value })} />
        </label>
        <label className="field">
          <span>Loại</span>
          <select value={meta.type} onChange={(e) => setMeta({ ...meta, type: e.target.value as DocumentType })}>
            {DOCUMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {DOCUMENT_TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Thư mục</span>
          <select value={meta.folderId} onChange={(e) => setMeta({ ...meta, folderId: e.target.value })}>
            <option value="">Chưa phân loại</option>
            {folders.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="button-secondary" disabled={!meta.title.trim()} onClick={saveMeta}>
          Lưu thông tin
        </button>
      </div>

      <div className="collab-bar" role="status">
        <span className={`collab-status collab-${status}`}>{STATUS[status]}</span>
        {session && !session.canEdit && status !== 'connecting' && <span className="badge badge-muted">Chỉ xem</span>}
        <ul className="collab-people" aria-label="Đang mở tài liệu">
          {people.map((p) => (
            <li key={p.id} style={{ borderColor: p.color }}>
              <span className="collab-dot" style={{ background: p.color }} />
              {p.self ? `${p.name} (bạn)` : p.name}
            </li>
          ))}
        </ul>
      </div>
      {session?.error && <Alert>{session.error}</Alert>}
      <div className="collab-host" ref={host} aria-label="Soạn thảo tài liệu" />
      <p className="muted small">
        Viết bằng Markdown. Mọi người cùng sửa một lúc không ghi đè lên nhau; con trỏ của người khác hiện kèm tên.
        {session?.savedAt && ` Đã lưu nháp lúc ${session.savedAt.toLocaleTimeString('vi-VN')}.`}
      </p>
      <label className="field">
        <span>Ghi chú cho phiên bản (không bắt buộc)</span>
        <input value={note} maxLength={200} placeholder="Ví dụ: chốt yêu cầu đăng nhập" onChange={(e) => setNote(e.target.value)} />
      </label>
      {message && <Alert kind={message.ok ? 'success' : 'error'}>{message.text}</Alert>}
      <div className="actions">
        <button type="button" onClick={saveVersion} disabled={!session?.canEdit}>
          Lưu vào lịch sử
        </button>
        <button type="button" className="button-secondary" onClick={finish}>
          Xong
        </button>
      </div>
    </section>
  )
}
