import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../../api/client'
import { errorMessage } from '../../api/errors'
import type { DocumentDetail, DocumentFolder, DocumentSummary, DocumentType, Page } from '../../api/types'
import { useResource } from '../../api/useResource'
import { Alert, Field } from '../../components/Form'
import { ListSkeleton } from '../../components/Skeleton'
import { useProject } from '../project/projectContext'
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS, formatWhen } from './labels'
import { useLiveReload } from '../../realtime/useLiveReload'

const ALL = 'all'
const UNFILED = 'none'

/** Folders in tree order with their depth, so the sidebar can indent them. */
function flatten(folders: DocumentFolder[]): { folder: DocumentFolder; depth: number }[] {
  const out: { folder: DocumentFolder; depth: number }[] = []
  const walk = (parentId: string | null, depth: number) => {
    for (const folder of folders.filter((f) => f.parentId === parentId)) {
      out.push({ folder, depth })
      walk(folder.id, depth + 1)
    }
  }
  walk(null, 0)
  return out
}

export function DocumentsPage() {
  const { project } = useProject()
  const navigate = useNavigate()
  const canEdit = project.permissions.includes('EDIT_DOCUMENTS') && project.status !== 'ARCHIVED'
  const folders = useResource<DocumentFolder[]>(`/api/projects/${project.id}/document-folders`)
  const [selected, setSelected] = useState(ALL)
  const [q, setQ] = useState('')
  const [debouncedQ, setDebouncedQ] = useState('')
  const [type, setType] = useState<DocumentType | ''>('')
  const [archived, setArchived] = useState(false)
  const [size, setSize] = useState(50)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQ(q.trim()), 300)
    return () => clearTimeout(timer)
  }, [q])

  const query = new URLSearchParams({ size: String(size), status: archived ? 'ARCHIVED' : 'ACTIVE' })
  if (debouncedQ) query.set('q', debouncedQ)
  if (type) query.set('type', type)
  if (selected === UNFILED) query.set('unfiled', 'true')
  else if (selected !== ALL) query.set('folderId', selected)
  const list = useResource<Page<DocumentSummary>>(`/api/projects/${project.id}/documents?${query}`)
  useLiveReload(project.id, ['documents'], () => {
    list.reload()
    folders.reload()
  })

  const tree = useMemo(() => flatten(folders.data ?? []), [folders.data])
  const current = folders.data?.find((f) => f.id === selected)
  const filtered = !!(debouncedQ || type || archived)

  return (
    <div className="docs-layout">
      <aside className="docs-sidebar card">
        <nav aria-label="Thư mục">
          <button type="button" className={`folder-item ${selected === ALL ? 'on' : ''}`} onClick={() => (setSelected(ALL), setSize(50))}>
            Tất cả tài liệu
          </button>
          <button type="button" className={`folder-item ${selected === UNFILED ? 'on' : ''}`} onClick={() => (setSelected(UNFILED), setSize(50))}>
            Chưa phân loại
          </button>
          {tree.map(({ folder, depth }) => (
            <button
              key={folder.id}
              type="button"
              className={`folder-item ${selected === folder.id ? 'on' : ''}`}
              style={{ paddingLeft: `${0.75 + depth * 0.9}rem` }}
              onClick={() => (setSelected(folder.id), setSize(50))}
            >
              <span>{folder.name}</span>
              <span className="muted small">{folder.documentCount}</span>
            </button>
          ))}
        </nav>
        {folders.error && <Alert>{folders.error}</Alert>}
        {canEdit && (
          <FolderTools
            projectId={project.id}
            folders={folders.data ?? []}
            current={current}
            onChanged={() => folders.reload()}
            onDeleted={() => setSelected(ALL)}
          />
        )}
      </aside>

      <section className="stack">
        <div className="filters">
          <input type="search" placeholder="Tìm theo tiêu đề hoặc nội dung…" value={q} onChange={(e) => (setQ(e.target.value), setSize(50))} aria-label="Tìm tài liệu" />
          <select value={type} onChange={(e) => (setType(e.target.value as DocumentType | ''), setSize(50))} aria-label="Loại tài liệu">
            <option value="">Mọi loại</option>
            {DOCUMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {DOCUMENT_TYPE_LABELS[t]}
              </option>
            ))}
          </select>
          <label className="inline-check">
            <input type="checkbox" checked={archived} onChange={(e) => (setArchived(e.target.checked), setSize(50))} /> Đã lưu trữ
          </label>
          {canEdit && (
            <button type="button" className="docs-new" onClick={() => setCreating((v) => !v)}>
              Tài liệu mới
            </button>
          )}
        </div>

        {creating && (
          <NewDocument
            projectId={project.id}
            folders={folders.data ?? []}
            defaultFolder={current?.id ?? ''}
            onCreated={(doc) => navigate(doc.id)}
            onCancel={() => setCreating(false)}
          />
        )}

        <Alert>{list.error}</Alert>
        {!list.data && !list.error && <ListSkeleton />}
        {list.data && list.data.items.length === 0 && (
          <section className="card empty-state">
            <h2>{filtered ? 'Không có tài liệu phù hợp' : 'Chưa có tài liệu nào ở đây'}</h2>
            <p className="muted">
              {filtered
                ? 'Thử đổi từ khóa hoặc bỏ bớt bộ lọc.'
                : canEdit
                  ? 'Tạo tài liệu đầu tiên cho dự án: charter, yêu cầu, thiết kế hay biên bản họp.'
                  : 'Khi có người tạo tài liệu, nó sẽ xuất hiện ở đây.'}
            </p>
          </section>
        )}
        <ul className="plain-list doc-list">
          {list.data?.items.map((doc) => (
            <li key={doc.id}>
              <Link to={doc.id} className="card doc-card">
                <div className="doc-card-main">
                  <div className="doc-card-title">
                    <strong>{doc.title}</strong>
                    <span className="badge">{DOCUMENT_TYPE_LABELS[doc.type]}</span>
                    {doc.status === 'ARCHIVED' && <span className="badge badge-muted">Đã lưu trữ</span>}
                  </div>
                  {doc.excerpt && <p className="muted doc-excerpt">{doc.excerpt}</p>}
                  <p className="muted small">
                    {doc.updatedBy?.fullName ?? 'Ai đó'} cập nhật {formatWhen(doc.updatedAt)} · phiên bản {doc.version}
                    {doc.attachmentCount > 0 && ` · ${doc.attachmentCount} tệp`}
                  </p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
        {list.data && list.data.total > list.data.items.length && (
          <button type="button" className="button-secondary" onClick={() => setSize((s) => s + 50)}>
            Xem thêm ({list.data.items.length}/{list.data.total})
          </button>
        )}
      </section>
    </div>
  )
}

function FolderTools({
  projectId,
  folders,
  current,
  onChanged,
  onDeleted,
}: {
  projectId: string
  folders: DocumentFolder[]
  current: DocumentFolder | undefined
  onChanged: () => void
  onDeleted: () => void
}) {
  const [mode, setMode] = useState<'new' | 'rename' | null>(null)
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      if (mode === 'rename' && current) await api(`/api/document-folders/${current.id}`, { method: 'PUT', body: { name } })
      else await api(`/api/projects/${projectId}/document-folders`, { method: 'POST', body: { name, parentId: parentId || null } })
      setMode(null)
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function remove() {
    if (!current || !window.confirm(`Xóa thư mục "${current.name}"?`)) return
    setError(null)
    try {
      await api(`/api/document-folders/${current.id}`, { method: 'DELETE' })
      onDeleted()
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <div className="folder-tools stack">
      {mode ? (
        <form onSubmit={submit} className="stack">
          <Field label={mode === 'rename' ? 'Tên mới' : 'Tên thư mục'} required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
          {mode === 'new' && (
            <label className="field">
              <span>Nằm trong</span>
              <select value={parentId} onChange={(e) => setParentId(e.target.value)}>
                <option value="">Gốc</option>
                {flatten(folders).map(({ folder, depth }) => (
                  <option key={folder.id} value={folder.id}>
                    {'  '.repeat(depth)}
                    {folder.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="actions">
            <button type="submit" className="button-small">
              Lưu
            </button>
            <button type="button" className="button-secondary button-small" onClick={() => setMode(null)}>
              Hủy
            </button>
          </div>
        </form>
      ) : (
        <div className="actions">
          <button type="button" className="button-secondary button-small" onClick={() => (setName(''), setParentId(current?.id ?? ''), setError(null), setMode('new'))}>
            Thư mục mới
          </button>
          {current && (
            <>
              <button type="button" className="button-secondary button-small" onClick={() => (setName(current.name), setError(null), setMode('rename'))}>
                Đổi tên
              </button>
              <button type="button" className="button-danger button-small" onClick={remove}>
                Xóa
              </button>
            </>
          )}
        </div>
      )}
      <Alert>{error}</Alert>
    </div>
  )
}

function NewDocument({
  projectId,
  folders,
  defaultFolder,
  onCreated,
  onCancel,
}: {
  projectId: string
  folders: DocumentFolder[]
  defaultFolder: string
  onCreated: (doc: DocumentDetail) => void
  onCancel: () => void
}) {
  const [title, setTitle] = useState('')
  const [type, setType] = useState<DocumentType>('OTHER')
  const [folderId, setFolderId] = useState(defaultFolder)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onCreated(await api<DocumentDetail>(`/api/projects/${projectId}/documents`, { method: 'POST', body: { title, type, folderId: folderId || null, content: '' } }))
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="card stack">
      <h2>Tài liệu mới</h2>
      <Field label="Tiêu đề" required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} autoFocus />
      <div className="inline-form">
        <label className="field">
          <span>Loại</span>
          <select value={type} onChange={(e) => setType(e.target.value as DocumentType)}>
            {DOCUMENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {DOCUMENT_TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Thư mục</span>
          <select value={folderId} onChange={(e) => setFolderId(e.target.value)}>
            <option value="">Chưa phân loại</option>
            {flatten(folders).map(({ folder, depth }) => (
              <option key={folder.id} value={folder.id}>
                {'  '.repeat(depth)}
                {folder.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <Alert>{error}</Alert>
      <div className="actions">
        <button type="submit" disabled={busy || !title.trim()}>
          Tạo và bắt đầu viết
        </button>
        <button type="button" className="button-secondary" onClick={onCancel}>
          Hủy
        </button>
      </div>
    </form>
  )
}
