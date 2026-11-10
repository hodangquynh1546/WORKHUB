import { useState } from 'react'
import { Tooltip } from './ChartShell'
import { fmt, niceScale, toneColor, useWidth, type Tone } from './chartUtils'

export type ScatterPoint = { id: string; x: number; y: number; tone: Tone; title: string; detail: string }

const M = { l: 42, r: 16, t: 14, b: 30 }

/** Dots over a numeric x axis (days) with dashed reference lines; hover picks the nearest dot. */
export function ScatterChart({
  points,
  xMin,
  xMax,
  formatX,
  lines,
  label,
  height = 280,
}: {
  points: ScatterPoint[]
  xMin: number
  xMax: number
  formatX: (x: number) => string
  lines: { value: number; name: string; tone: Tone }[]
  label: string
  height?: number
}) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const [hover, setHover] = useState<number | null>(null)
  const w = Math.max(0, width - M.l - M.r)
  const h = height - M.t - M.b
  const max = Math.max(0, ...points.map((p) => p.y), ...lines.map((l) => l.value))
  const { top, ticks } = niceScale(max, false)
  const span = Math.max(1, xMax - xMin)
  const x = (v: number) => M.l + ((v - xMin) / span) * w
  const y = (v: number) => M.t + h - (v / top) * h
  const xTicks = Array.from({ length: 5 }, (_, i) => xMin + (span * i) / 4)

  function nearest(clientX: number, clientY: number, rect: DOMRect) {
    let best = -1
    let bestDistance = 24
    points.forEach((p, i) => {
      const d = Math.hypot(x(p.x) - (clientX - rect.left), y(p.y) - (clientY - rect.top))
      if (d < bestDistance) {
        best = i
        bestDistance = d
      }
    })
    setHover(best < 0 ? null : best)
  }

  const active = hover != null ? points[hover] : null
  return (
    <div className="chart" ref={ref} style={{ height }}>
      {width > 0 && (
        <svg
          width={width}
          height={height}
          role="img"
          aria-label={label}
          onPointerMove={(e) => nearest(e.clientX, e.clientY, e.currentTarget.getBoundingClientRect())}
          onPointerLeave={() => setHover(null)}
        >
          {ticks.map((t) => (
            <g key={t}>
              <line x1={M.l} x2={M.l + w} y1={y(t)} y2={y(t)} className="chart-grid" />
              <text x={M.l - 8} y={y(t)} className="chart-axis" textAnchor="end" dominantBaseline="middle">
                {fmt(t)}
              </text>
            </g>
          ))}
          {xTicks.map((t) => (
            <text key={t} x={x(t)} y={height - 8} className="chart-axis" textAnchor="middle">
              {formatX(Math.round(t))}
            </text>
          ))}
          {lines.map((l) => (
            <g key={l.name} pointerEvents="none">
              <line x1={M.l} x2={M.l + w} y1={y(l.value)} y2={y(l.value)} className="chart-ref" stroke={toneColor(l.tone)} />
              <text x={M.l + 4} y={y(l.value) - 6} className="chart-axis">
                {l.name}: {fmt(l.value)} ngày
              </text>
            </g>
          ))}
          {points.map((p, i) => (
            <circle
              key={p.id}
              cx={x(p.x)}
              cy={y(p.y)}
              r={hover === i ? 6 : 4.5}
              fill={toneColor(p.tone)}
              className="chart-dot"
              pointerEvents="none"
            />
          ))}
        </svg>
      )}
      {active && width > 0 && (
        <Tooltip x={x(active.x)} y={y(active.y)} boundary={width}>
          <strong>{active.title}</strong>
          <div className="muted small">{active.detail}</div>
        </Tooltip>
      )}
    </div>
  )
}
