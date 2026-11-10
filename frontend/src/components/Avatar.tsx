/** Round profile picture, or the person's initial when they have none. */
export function Avatar({ name, url, size }: { name: string; url?: string | null; size?: 'lg' }) {
  const className = `avatar${size ? ` avatar-${size}` : ''}`
  return url ? (
    <img className={className} src={url} alt="" title={name} />
  ) : (
    <span className={className} title={name} aria-hidden={size ? undefined : true}>
      {name.trim().charAt(0).toUpperCase()}
    </span>
  )
}
