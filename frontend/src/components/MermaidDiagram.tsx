import { useEffect, useState } from 'react'

let counter = 0

/**
 * Mount it with `key={source}` so a changed diagram starts from a clean state.
 * Renders a ```mermaid block as an SVG diagram. mermaid is loaded on demand (it is large) and runs with
 * securityLevel "strict", which sanitises the generated SVG; if the source cannot be drawn the code is shown instead.
 */
export function MermaidDiagram({ source }: { source: string }) {
  const [svg, setSvg] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const mermaid = (await import('mermaid')).default
        const dark = window.matchMedia('(prefers-color-scheme: dark)').matches
        mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: dark ? 'dark' : 'neutral' })
        const { svg: out } = await mermaid.render(`mmd-${++counter}`, source)
        if (!cancelled) setSvg(out)
      } catch {
        if (!cancelled) setFailed(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [source])

  if (failed) {
    return (
      <pre>
        <code>{source}</code>
      </pre>
    )
  }
  if (!svg) return <div className="mermaid muted small">Đang vẽ sơ đồ…</div>
  return <div className="mermaid" role="img" aria-label="Sơ đồ" dangerouslySetInnerHTML={{ __html: svg }} />
}
