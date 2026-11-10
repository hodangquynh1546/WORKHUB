import { useState, type ReactNode } from 'react'
import { Tooltip } from './ChartShell'
import { columnPath, fmt, niceScale, toneColor, useWidth, type Tone } from './chartUtils'

export type ColumnSeries = { id: string; name: string; values: number[]; tone: Tone }

const M = { l: 42, r: 16, t: 20, b: 34 }

/** Grouped columns (<= 24px wide, rounded tops, 2px gaps) with an optional dashed reference line. */
export function ColumnChart({
  categories,
  series,
  label,
  reference,
  extra,
  height = 280,
}: {
  categories: string[]
  series: ColumnSeries[]
  label: string
  reference?: { value: number; name: string }
  extra?: (index: number) => ReactNode
  height?: number
}) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const [hover, setHover] = useState<number | null>(null)
  const n = categories.length
  const w = Math.max(0, width - M.l - M.r)
  const h = height - M.t - M.b
  const max = Math.max(0, reference?.value ?? 0, ...series.flatMap((s) => s.values))
  const { top, ticks } = niceScale(max)
  const y = (v: number) => M.t + h - (v / top) * h
  const slot = n > 0 ? w / n : 0
  const barW = Math.max(4, Math.min(24, (slot * 0.7 - 2 * (series.length - 1)) / series.length))
  const groupW = barW * series.length + 2 * (series.length - 1)
  const labelValues = n <= 10 && series.length <= 2 && barW >= 14
  const every = Math.max(1, Math.ceil(n / Math.max(1, Math.floor(w / 64))))

  return (
    <div className="chart" ref={ref} style={{ height }}>
      {width > 0 && (
        <svg width={width} height={height} role="img" aria-label={label} onPointerLeave={() => setHover(null)}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={M.l} x2={M.l + w} y1={y(t)} y2={y(t)} className="chart-grid" />
              <text x={M.l - 8} y={y(t)} className="chart-axis" textAnchor="end" dominantBaseline="middle">
                {fmt(t)}
              </text>
            </g>
          ))}
          {categories.map((c, i) => {
            const cx = M.l + slot * i + slot / 2
            return (
              <g key={i}>
                <rect
                  x={M.l + slot * i}
                  y={M.t}
                  width={slot}
                  height={h}
                  className={hover === i ? 'chart-hit chart-hit-on' : 'chart-hit'}
                  onPointerEnter={() => setHover(i)}
                />
                {series.map((s, k) => {
                  const bh = h - (y(s.values[i]) - M.t)
                  const bx = cx - groupW / 2 + k * (barW + 2)
                  return (
                    <g key={s.id} pointerEvents="none">
                      {bh > 0 && <path d={columnPath(bx, y(s.values[i]), barW, bh)} fill={toneColor(s.tone)} />}
                      {labelValues && (
                        <text x={bx + barW / 2} y={y(s.values[i]) - 6} className="chart-value" textAnchor="middle">
                          {fmt(s.values[i])}
                        </text>
                      )}
                    </g>
                  )
                })}
                {i % every === 0 && (
                  <text x={cx} y={height - 12} className="chart-axis" textAnchor="middle">
                    {c.length > 12 ? `${c.slice(0, 11)}…` : c}
                  </text>
                )}
              </g>
            )
          })}
          {reference && (
            <g pointerEvents="none">
              <line x1={M.l} x2={M.l + w} y1={y(reference.value)} y2={y(reference.value)} className="chart-ref" />
              <text x={M.l + 4} y={y(reference.value) - 6} className="chart-axis">
                {reference.name}: {fmt(reference.value)}
              </text>
            </g>
          )}
        </svg>
      )}
      {hover != null && width > 0 && (
        <Tooltip x={M.l + slot * hover + slot / 2} y={M.t + 4} boundary={width}>
          <strong>{categories[hover]}</strong>
          {series.map((s) => (
            <div key={s.id} className="chart-tip-row">
              <svg width="10" height="10" aria-hidden="true">
                <rect width="10" height="10" rx="2" fill={toneColor(s.tone)} />
              </svg>
              <span>{s.name}</span>
              <b>{fmt(s.values[hover])}</b>
            </div>
          ))}
          {extra?.(hover)}
        </Tooltip>
      )}
    </div>
  )
}
