import { Link } from 'react-router'
import type { DocumentSummary, LinkTargetType, Page } from '../../api/types'
import { useResource } from '../../api/useResource'
import { DOCUMENT_TYPE_LABELS } from './labels'

/** Documents attached to an issue or sprint; renders nothing when there are none. */
export function RelatedDocuments({ projectId, targetType, targetId }: { projectId: string; targetType: LinkTargetType; targetId: string }) {
  const { data } = useResource<Page<DocumentSummary>>(
    `/api/projects/${projectId}/documents?linkedType=${targetType}&linkedId=${targetId}&size=20`,
  )
  if (!data || data.items.length === 0) return null
  return (
    <section className="card stack">
      <h2>Tài liệu liên quan</h2>
      <ul className="plain-list">
        {data.items.map((doc) => (
          <li key={doc.id} className="row">
            <div>
              <Link to={`/projects/${projectId}/documents/${doc.id}`}>{doc.title}</Link>
              <p className="muted small">{DOCUMENT_TYPE_LABELS[doc.type]}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
