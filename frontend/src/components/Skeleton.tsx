/** Placeholder shown while data loads: keeps the layout stable instead of flashing "loading" text. */
export function PageSkeleton() {
  return (
    <div className="skeleton-page" role="status" aria-label="Đang tải">
      <div className="skeleton skeleton-title" />
      <div className="skeleton skeleton-line" />
      <div className="skeleton skeleton-block" />
    </div>
  )
}

export function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="skeleton-list" role="status" aria-label="Đang tải">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton skeleton-row" />
      ))}
    </div>
  )
}
