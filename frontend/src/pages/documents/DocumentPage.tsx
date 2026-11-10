import { lazy, Suspense, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, downloadFile } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type {
  DocumentAttachment,
  DocumentDetail,
  DocumentFolder,
  DocumentVersionDetail,
  DocumentVersionSummary,
  Issue,
  LinkTargetType,
  MeetingSummary,
  Page,
  Sprint,
} from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert } from '../../components/Form'
import { Markdown } from '../../components/Markdown'
import { PageSkeleton } from '../../components/Skeleton'
import { useProject } from '../project/projectContext'
import { DOCUMENT_TYPE_LABELS, formatBytes, formatWhen } from './labels'
import { diffLines } from './lineDiff'
import { useLiveReload } from '../../realtime/useLiveReload'
import { PrintButton } from '../../components/ExportMenu'
import { downloadText } from '../../api/downloadText'

// The live editor (CodeMirror + Yjs) is a separate chunk: it loads only when somebody starts editing.
const CollabEditor = lazy(() => import('./collab/CollabEditor'))

const MAX_FILE = 10 * 1024 * 1024

const LINK_LABEL: Record<LinkTargetType, string> = { ISSUE: 'Công việc', SPRINT: 'Sprint', MEETING: 'Cuộc họp' }
const LINK_HREF: Record<LinkTargetType, (projectId: string, id: string) => string> = {
  ISSUE: (_, id) => `/issues/${id}`,
  SPRINT: (projectId, id) => `/projects/${projectId}/sprints/${id}`,
  MEETING: (projectId, id) => `/projects/${projectId}/meetings/${id}`,
}

export function DocumentPage() {
  const { documentId } = useParams()
  const { project } = useProject()
  const navigate = useNavigate()
  const { data: doc, error, reload } = useResource<DocumentDetail>(`/api/documents/${documentId}`)
  const folders = useResource<DocumentFolder[]>(`/api/projects/${project.id}/document-folders`)
  useLiveReload(project.id, ['documents'], () => {
    // While editing live the editor keeps the text itself; only the details around it are refreshed.
    reload()
    folders.reload()
  })
  const [editing, setEditing] = useState(false)
  const [viewing, setViewing] = useState<DocumentVersionDetail | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  if (error) {
    return (
      <>
        <Alert>{error}</Alert>
        <Link to={`/projects/${project.id}/documents`}>← Danh sách tài liệu</Link>
      </>
    )
  }
  if (!doc) return <PageSkeleton />

  async function run(action: () => Promise<unknown>) {
    setActionError(null)
    try {
      await action()
      reload()
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  async function openVersion(number: number) {
    setActionError(null)
    try {
      setViewing(await api<DocumentVersionDetail>(`/api/documents/${doc!.id}/versions/${number}`))
    } catch (err) {
      setActionError(errorMessage(err))
    }
  }

  const archived = doc.status === 'ARCHIVED'
  return (
    <>
      <Link to={`/projects/${project.id}/documents`} className="muted-link">
        ← Tài liệu
      </Link>
      <header className="issue-header">
        <div>
          <div className="doc-card-title">
            <span className="badge">{DOCUMENT_TYPE_LABELS[doc.type]}</span>
            <span className="badge badge-muted">Phiên bản {doc.version}</span>
            {archived && <span className="badge badge-muted">Đã lưu trữ</span>}
          </div>
          <h1>{doc.title}</h1>
        </div>
        {!editing && (
          <div className="actions no-print">
            <PrintButton />
            <button type="button" className="button-secondary button-small" onClick={() => downloadText(`${doc.title}.md`, doc.content)}>
              Tải .md
            </button>
            {doc.canEdit && <button type="button" onClick={() => (setViewing(null), setEditing(true))}>Chỉnh sửa</button>}
            {project.permissions.includes('EDIT_DOCUMENTS') && project.status !== 'ARCHIVED' && (
              <button
                type="button"
                className="button-secondary"
                onClick={() => run(() => api(`/api/documents/${doc.id}/${archived ? 'unarchive' : 'archive'}`, { method: 'POST' }))}
              >
                {archived ? 'Bỏ lưu trữ' : 'Lưu trữ'}
              </button>
            )}
            {doc.canDelete && (
              <button
                type="button"
                className="button-danger"
                onClick={async () => {
                  if (!window.confirm('Xóa tài liệu này? Lịch sử phiên bản cũng sẽ không còn xem được.')) return
                  try {
                    await api(`/api/documents/${doc.id}`, { method: 'DELETE' })
                    navigate(`/projects/${project.id}/documents`)
                  } catch (err) {
                    setActionError(errorMessage(err))
                  }
                }}
              >
                Xóa
              </button>
            )}
          </div>
        )}
      </header>
      {archived && <Alert kind="info">Tài liệu đã lưu trữ: chỉ xem. Bỏ lưu trữ để sửa tiếp.</Alert>}
      <Alert>{actionError}</Alert>

      <div className="two-column">
        <div className="stack-lg">
          {editing ? (
            <Suspense fallback={<PageSkeleton />}>
              <CollabEditor doc={doc} folders={folders.data ?? []} onDone={() => (setEditing(false), reload())} onReload={async () => reload()} />
            </Suspense>
          ) : viewing ? (
            <VersionView
              viewing={viewing}
              current={doc}
              onClose={() => setViewing(null)}
              onRestore={
                doc.canEdit && viewing.version !== doc.version
                  ? () =>
                      run(async () => {
                        await api(`/api/documents/${doc.id}/versions/${viewing.version}/restore`, { method: 'POST' })
                        setViewing(null)
                      })
                  : undefined
              }
            />
          ) : (
            <section className="card">
              <Markdown source={doc.content} />
            </section>
          )}
        </div>

        <div className="stack-lg">
          <section className="card stack">
            <h2>Thông tin</h2>
            <dl className="facts">
              <dt>Người tạo</dt>
              <dd>{doc.createdBy?.fullName ?? '—'}</dd>
              <dt>Tạo lúc</dt>
              <dd>{formatWhen(doc.createdAt)}</dd>
              <dt>Sửa gần nhất</dt>
              <dd>
                {doc.updatedBy?.fullName ?? '—'}, {formatWhen(doc.updatedAt)}
              </dd>
              <dt>Thư mục</dt>
              <dd>{folders.data?.find((f) => f.id === doc.folderId)?.name ?? 'Chưa phân loại'}</dd>
            </dl>
          </section>
          <Attachments doc={doc} onChanged={reload} />
          <Links doc={doc} projectId={project.id} onChanged={reload} />
          <History
            doc={doc}
            viewingVersion={viewing?.version ?? null}
            onOpen={openVersion}
            onRestore={(n) => run(() => api(`/api/documents/${doc.id}/versions/${n}/restore`, { method: 'POST' }))}
          />
        </div>
      </div>
    </>
  )
}

// ---------- viewing an old version ----------

function VersionView({
  viewing,
  current,
  onClose,
  onRestore,
}: {
  viewing: DocumentVersionDetail
  current: DocumentDetail
  onClose: () => void
  onRestore?: () => void
}) {
  const [mode, setMode] = useState<'read' | 'diff'>('read')
  const diff = mode === 'diff' ? diffLines(viewing.content, current.content) : null
  const isCurrent = viewing.version === current.version
  return (
    <section className="card stack">
      <div className="section-header">
        <h2>
          Phiên bản {viewing.version}
          {isCurrent && ' (hiện tại)'}
        </h2>
        <div className="actions">
          {onRestore && (
            <button type="button" className="button-small" onClick={onRestore}>
              Khôi phục bản này
            </button>
          )}
          <button type="button" className="button-secondary button-small" onClick={onClose}>
            Đóng
          </button>
        </div>
      </div>
      <p className="muted small">
        {viewing.author?.fullName ?? 'Ai đó'} lưu lúc {formatWhen(viewing.createdAt)}
        {viewing.note && ` · ${viewing.note}`}
      </p>
      {!isCurrent && (
        <div className="segmented-control" role="group" aria-label="Cách xem">
          <button type="button" className={mode === 'read' ? 'on' : ''} onClick={() => setMode('read')}>
            Nội dung
          </button>
          <button type="button" className={mode === 'diff' ? 'on' : ''} onClick={() => setMode('diff')}>
            So với bản hiện tại
          </button>
        </div>
      )}
      {mode === 'read' || isCurrent ? (
        <>
          {viewing.title !== current.title && <p className="muted small">Tiêu đề lúc đó: {viewing.title}</p>}
          <Markdown source={viewing.content} />
        </>
      ) : diff ? (
        <DiffView lines={diff} />
      ) : (
        <Alert kind="info">Văn bản quá dài để so sánh từng dòng. Hãy xem từng phiên bản riêng.</Alert>
      )}
    </section>
  )
}

/** Changed lines with two lines of context; long unchanged stretches are folded. */
function DiffView({ lines }: { lines: { kind: 'same' | 'add' | 'del'; text: string }[] }) {
  const changed = lines.some((l) => l.kind !== 'same')
  if (!changed) return <p className="muted">Hai phiên bản có nội dung giống nhau.</p>
  const show = lines.map((l, i) => l.kind !== 'same' || lines.slice(Math.max(0, i - 2), i + 3).some((n) => n.kind !== 'same'))
  const rows: ReactNode[] = []
  lines.forEach((line, i) => {
    if (show[i]) rows.push(<div key={i} className={`diff-line diff-${line.kind}`}>{line.kind === 'add' ? '+ ' : line.kind === 'del' ? '− ' : '  '}{line.text || ' '}</div>)
    else if (show[i - 1]) rows.push(<div key={i} className="diff-fold">…</div>)
  })
  return (
    <div className="diff" role="region" aria-label="So sánh phiên bản">
      <p className="muted small">Dòng đỏ chỉ có ở phiên bản này, dòng xanh chỉ có ở bản hiện tại.</p>
      {rows}
    </div>
  )
}

// ---------- sidebar sections ----------

function Attachments({ doc, onChanged }: { doc: DocumentDetail; onChanged: () => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function upload(files: FileList | null) {
    if (!files?.length) return
    setError(null)
    setBusy(true)
    try {
      for (const file of Array.from(files)) {
        if (file.size > MAX_FILE) throw new Error(`"${file.name}" lớn hơn 10 MB.`)
        const form = new FormData()
        form.append('file', file)
        await api(`/api/documents/${doc.id}/attachments`, { method: 'POST', body: form })
      }
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function act(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <section className="card stack">
      <div className="section-header">
        <h2>Tệp đính kèm</h2>
        {doc.canEdit && (
          <>
            <button type="button" className="button-secondary button-small" disabled={busy} onClick={() => input.current?.click()}>
              Tải tệp lên
            </button>
            <input ref={input} type="file" multiple hidden onChange={(e) => (void upload(e.target.files), (e.target.value = ''))} />
          </>
        )}
      </div>
      {doc.attachments.length === 0 && <p className="muted small">Chưa có tệp nào. Mỗi tệp tối đa 10 MB.</p>}
      <ul className="plain-list">
        {doc.attachments.map((a: DocumentAttachment) => (
          <li key={a.id} className="row">
            <div>
              <button type="button" className="link-button" onClick={() => act(() => downloadFile(`/api/documents/${doc.id}/attachments/${a.id}`, a.fileName))}>
                {a.fileName}
              </button>
              <p className="muted small">
                {formatBytes(a.sizeBytes)} · {a.uploadedBy?.fullName ?? 'Ai đó'}, {formatWhen(a.createdAt)}
              </p>
            </div>
            {doc.canEdit && (
              <button
                type="button"
                className="button-danger button-small"
                onClick={() => window.confirm(`Xóa "${a.fileName}"?`) && act(() => api(`/api/documents/${doc.id}/attachments/${a.id}`, { method: 'DELETE' }))}
              >
                Xóa
              </button>
            )}
          </li>
        ))}
      </ul>
      <Alert>{error}</Alert>
    </section>
  )
}

function Links({ doc, projectId, onChanged }: { doc: DocumentDetail; projectId: string; onChanged: () => void }) {
  const [adding, setAdding] = useState(false)
  const [kind, setKind] = useState<LinkTargetType>('ISSUE')
  const [error, setError] = useState<string | null>(null)

  async function change(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  const link = (type: LinkTargetType, targetId: string) =>
    change(async () => {
      await api(`/api/documents/${doc.id}/links`, { method: 'POST', body: { targetType: type, targetId } })
      setAdding(false)
    })

  const linked = new Set(doc.links.map((l) => l.targetId))
  return (
    <section className="card stack">
      <div className="section-header">
        <h2>Gắn với</h2>
        {doc.canEdit && (
          <button type="button" className="button-secondary button-small" onClick={() => setAdding((v) => !v)}>
            {adding ? 'Đóng' : 'Thêm'}
          </button>
        )}
      </div>
      {doc.links.length === 0 && !adding && <p className="muted small">Chưa gắn với công việc hay sprint nào.</p>}
      <ul className="plain-list">
        {doc.links.map((l) => (
          <li key={`${l.targetType}-${l.targetId}`} className="row">
            <div>
              <Link to={LINK_HREF[l.targetType](projectId, l.targetId)}>{l.label}</Link>
              <p className="muted small">{LINK_LABEL[l.targetType]}</p>
            </div>
            {doc.canEdit && (
              <button type="button" className="button-secondary button-small" onClick={() => change(() => api(`/api/documents/${doc.id}/links/${l.targetType}/${l.targetId}`, { method: 'DELETE' }))}>
                Gỡ
              </button>
            )}
          </li>
        ))}
      </ul>
      {adding && (
        <div className="stack">
          <div className="segmented-control" role="group" aria-label="Loại đối tượng">
            <button type="button" className={kind === 'ISSUE' ? 'on' : ''} onClick={() => setKind('ISSUE')}>
              Công việc
            </button>
            <button type="button" className={kind === 'SPRINT' ? 'on' : ''} onClick={() => setKind('SPRINT')}>
              Sprint
            </button>
            <button type="button" className={kind === 'MEETING' ? 'on' : ''} onClick={() => setKind('MEETING')}>
              Cuộc họp
            </button>
          </div>
          {kind === 'ISSUE' ? (
            <IssuePicker projectId={projectId} linked={linked} onPick={(id) => link('ISSUE', id)} />
          ) : kind === 'SPRINT' ? (
            <SprintPicker projectId={projectId} linked={linked} onPick={(id) => link('SPRINT', id)} />
          ) : (
            <MeetingPicker projectId={projectId} linked={linked} onPick={(id) => link('MEETING', id)} />
          )}
        </div>
      )}
      <Alert>{error}</Alert>
    </section>
  )
}

function IssuePicker({ projectId, linked, onPick }: { projectId: string; linked: Set<string>; onPick: (id: string) => void }) {
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(q.trim()), 300)
    return () => clearTimeout(timer)
  }, [q])
  return (
    <>
      <input type="search" placeholder="Tìm công việc theo tên hoặc mã…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Tìm công việc" autoFocus />
      {debounced && <IssueResults projectId={projectId} q={debounced} linked={linked} onPick={onPick} />}
    </>
  )
}

function IssueResults({ projectId, q, linked, onPick }: { projectId: string; q: string; linked: Set<string>; onPick: (id: string) => void }) {
  const { data, error } = useResource<Page<Issue>>(`/api/projects/${projectId}/issues?q=${encodeURIComponent(q)}&size=8`)
  const items = data?.items.filter((i) => !linked.has(i.id))
  return (
    <>
      <Alert>{error}</Alert>
      {items?.length === 0 && <p className="muted small">Không tìm thấy công việc nào.</p>}
      <ul className="plain-list picker-list">
        {items?.map((i) => (
          <li key={i.id} className="row">
            <span>
              <strong>{i.key}</strong> {i.title}
            </span>
            <button type="button" className="button-small" onClick={() => onPick(i.id)}>
              Gắn
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}

function MeetingPicker({ projectId, linked, onPick }: { projectId: string; linked: Set<string>; onPick: (id: string) => void }) {
  const { data, error } = useResource<MeetingSummary[]>(`/api/projects/${projectId}/meetings?newestFirst=true`)
  const items = data?.filter((m) => !linked.has(m.id))
  return (
    <>
      <Alert>{error}</Alert>
      {items?.length === 0 && <p className="muted small">Không còn cuộc họp nào để gắn.</p>}
      <ul className="plain-list picker-list">
        {items?.map((m) => (
          <li key={m.id} className="row">
            <span>
              {m.title} <span className="muted small">{formatWhen(m.startsAt)}</span>
            </span>
            <button type="button" className="button-small" onClick={() => onPick(m.id)}>
              Gắn
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}

function SprintPicker({ projectId, linked, onPick }: { projectId: string; linked: Set<string>; onPick: (id: string) => void }) {
  const { data, error } = useResource<Sprint[]>(`/api/projects/${projectId}/sprints`)
  const items = data?.filter((s) => !linked.has(s.id))
  return (
    <>
      <Alert>{error}</Alert>
      {items?.length === 0 && <p className="muted small">Không còn sprint nào để gắn.</p>}
      <ul className="plain-list picker-list">
        {items?.map((s) => (
          <li key={s.id} className="row">
            <span>{s.name}</span>
            <button type="button" className="button-small" onClick={() => onPick(s.id)}>
              Gắn
            </button>
          </li>
        ))}
      </ul>
    </>
  )
}

function History({
  doc,
  viewingVersion,
  onOpen,
  onRestore,
}: {
  doc: DocumentDetail
  viewingVersion: number | null
  onOpen: (version: number) => void
  onRestore: (version: number) => void
}) {
  // The revision in the path refetches the list after every save or restore.
  const { data, error } = useResource<DocumentVersionSummary[]>(`/api/documents/${doc.id}/versions?rev=${doc.version}`)
  return (
    <section className="card stack">
      <h2>Lịch sử phiên bản</h2>
      <Alert>{error}</Alert>
      <ul className="plain-list">
        {data?.map((v) => (
          <li key={v.version} className={`row version-row ${viewingVersion === v.version ? 'on' : ''}`}>
            <div>
              <button type="button" className="link-button" onClick={() => onOpen(v.version)}>
                Phiên bản {v.version}
                {v.version === doc.version && ' (hiện tại)'}
              </button>
              <p className="muted small">
                {v.author?.fullName ?? 'Ai đó'}, {formatWhen(v.createdAt)}
              </p>
              {v.note && <p className="small">{v.note}</p>}
            </div>
            {doc.canEdit && v.version !== doc.version && (
              <button
                type="button"
                className="button-secondary button-small"
                onClick={() => window.confirm(`Khôi phục phiên bản ${v.version}? Nó sẽ trở thành phiên bản mới nhất, lịch sử vẫn được giữ.`) && onRestore(v.version)}
              >
                Khôi phục
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
