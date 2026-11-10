import { useState, type KeyboardEvent } from 'react'
import { Tooltip } from './ChartShell'
import { fmt, linePath, niceScale, toneColor, useWidth, type Tone } from './chartUtils'

export type Series = {
  id: string
  name: string
  values: (number | null)[]
  tone: Tone
  dash?: boolean
  /** Area series are stacked in the order given (first = bottom); the rest are drawn as lines on top. */
  area?: boolean
}

const M = { l: 42, r: 16, t: 12, b: 30 }

/** Lines and stacked areas over a shared category axis (days). Hover or arrow keys move a crosshair + tooltip. */
export function TimeSeriesChart({
  labels,
  titles,
  series,
  label,
  height = 280,
  integer = true,
}: {
  labels: string[]
  titles: string[]
  series: Series[]
  label: string
  height?: number
  integer?: boolean
}) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const [hover, setHover] = useState<number | null>(null)
  const n = labels.length
  const w = Math.max(0, width - M.l - M.r)
  const h = height - M.t - M.b

  const areas = series.filter((s) => s.area)
  const lines = series.filter((s) => !s.area)
  // Cumulative tops of the stacked areas; null when any layer has no value that day.
  const tops: (number | null)[][] = areas.map((_, j) =>
    labels.map((_, i) => {
      let sum = 0
      for (let k = 0; k <= j; k++) {
        const v = areas[k].values[i]
        if (v == null) return null
        sum += v
      }
      return sum
    }),
  )
  const all = [...lines.flatMap((s) => s.values), ...(tops.at(-1) ?? [])].filter((v): v is number => v != null)
  const { top, ticks } = niceScale(Math.max(0, ...all), integer)

  const x = (i: number) => (n > 1 ? M.l + (i * w) / (n - 1) : M.l + w / 2)
  const y = (v: number) => M.t + h - (v / top) * h
  const every = Math.max(1, Math.ceil(n / Math.max(1, Math.floor(w / 56))))

  function areaPath(j: number) {
    const upper = tops[j]
    const lower = j === 0 ? labels.map(() => 0) : tops[j - 1]
    let d = ''
    let start = -1
    for (let i = 0; i <= n; i++) {
      const ok = i < n && upper[i] != null && lower[i] != null
      if (ok && start < 0) start = i
      if (!ok && start >= 0) {
        const idx = Array.from({ length: i - start }, (_, k) => start + k)
        d +=
          'M' +
          idx.map((k) => `${x(k).toFixed(1)},${y(upper[k] as number).toFixed(1)}`).join('L') +
          'L' +
          [...idx].reverse().map((k) => `${x(k).toFixed(1)},${y(lower[k] as number).toFixed(1)}`).join('L') +
          'Z'
        start = -1
      }
    }
    return d
  }

  function move(clientX: number, rect: DOMRect) {
    if (n < 2 || w <= 0) return
    const i = Math.round(((clientX - rect.left - M.l) / w) * (n - 1))
    setHover(Math.min(n - 1, Math.max(0, i)))
  }

  function onKey(e: KeyboardEvent) {
    if (e.key === 'ArrowRight') setHover((i) => Math.min(n - 1, (i ?? -1) + 1))
    else if (e.key === 'ArrowLeft') setHover((i) => Math.max(0, (i ?? n) - 1))
    else if (e.key === 'Escape') setHover(null)
  }

  return (
    <div className="chart" ref={ref} style={{ height }}>
      {width > 0 && (
        <svg
          width={width}
          height={height}
          role="img"
          aria-label={label}
          tabIndex={0}
          onKeyDown={onKey}
          onBlur={() => setHover(null)}
          onPointerMove={(e) => move(e.clientX, e.currentTarget.getBoundingClientRect())}
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
          {labels.map(
            (l, i) =>
              i % every === 0 && (
                <text key={i} x={x(i)} y={height - 8} className="chart-axis" textAnchor="middle">
                  {l}
                </text>
              ),
          )}
          {areas
            .map((s, j) => (
              <path key={s.id} d={areaPath(j)} fill={toneColor(s.tone)} className="chart-area" />
            ))
            .reverse()}
          {lines.map((s) => (
            <path
              key={s.id}
              d={linePath(s.values, x, y)}
              fill="none"
              stroke={toneColor(s.tone)}
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray={s.dash ? '5 4' : undefined}
            />
          ))}
          {hover != null && (
            <g pointerEvents="none">
              <line x1={x(hover)} x2={x(hover)} y1={M.t} y2={M.t + h} className="chart-cross" />
              {lines.map(
                (s) =>
                  s.values[hover] != null && (
                    <circle
                      key={s.id}
                      cx={x(hover)}
                      cy={y(s.values[hover] as number)}
                      r={4}
                      fill={toneColor(s.tone)}
                      className="chart-dot"
                    />
                  ),
              )}
            </g>
          )}
        </svg>
      )}
      {hover != null && width > 0 && (
        <Tooltip x={x(hover)} y={M.t + 4} boundary={width}>
          <strong>{titles[hover]}</strong>
          {series.map((s) => (
            <div key={s.id} className="chart-tip-row">
              <svg width="14" height="8" aria-hidden="true">
                {s.area ? (
                  <rect width="14" height="8" rx="2" fill={toneColor(s.tone)} />
                ) : (
                  <line x1="0" y1="4" x2="14" y2="4" stroke={toneColor(s.tone)} strokeWidth="2" strokeDasharray={s.dash ? '4 3' : undefined} />
                )}
              </svg>
              <span>{s.name}</span>
              <b>{s.values[hover] == null ? '–' : fmt(s.values[hover] as number)}</b>
            </div>
          ))}
        </Tooltip>
      )}
    </div>
  )
}
