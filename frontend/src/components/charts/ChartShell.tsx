import { useRef, useState, type ReactNode } from 'react'
import { exportChart } from './exportChart'
import { toneColor, type Tone } from './chartUtils'

export type LegendItem = { name: string; tone: Tone; dash?: boolean; area?: boolean; dot?: boolean }

/** Title, optional note, legend (always shown for two or more series) and a switch to the data table. */
export function ChartShell({
  title,
  note,
  legend,
  head,
  rows,
  children,
}: {
  title: string
  note?: ReactNode
  legend?: LegendItem[]
  head: string[]
  rows: (string | number)[][]
  children: ReactNode
}) {
  const [table, setTable] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const root = useRef<HTMLElement>(null)
  async function save(format: 'svg' | 'png') {
    setError(null)
    try {
      await exportChart(root.current!, format, title)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không xuất được biểu đồ.')
    }
  }
  return (
    <section className="card chart-card" ref={root}>
      <div className="section-header">
        <div>
          <h2>{title}</h2>
          {note && <p className="muted small chart-note">{note}</p>}
        </div>
        <div className="actions no-print">
          {!table && (
            <>
              <button type="button" className="button-secondary button-small" onClick={() => save('svg')}>
                SVG
              </button>
              <button type="button" className="button-secondary button-small" onClick={() => save('png')}>
                PNG
              </button>
            </>
          )}
          <button type="button" className="button-secondary button-small" onClick={() => setTable((v) => !v)}>
            {table ? 'Xem biểu đồ' : 'Xem dạng bảng'}
          </button>
        </div>
      </div>
      {error && <p className="text-danger small">{error}</p>}
      {legend && legend.length > 1 && (
        <ul className="chart-legend">
          {legend.map((item) => (
            <li key={item.name}>
              <svg width="22" height="10" aria-hidden="true">
                {item.area ? (
                  <rect x="0" y="1" width="22" height="8" rx="2" fill={toneColor(item.tone)} />
                ) : item.dot ? (
                  <circle cx="11" cy="5" r="4" fill={toneColor(item.tone)} />
                ) : (
                  <line
                    x1="0"
                    y1="5"
                    x2="22"
                    y2="5"
                    stroke={toneColor(item.tone)}
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeDasharray={item.dash ? '5 4' : undefined}
                  />
                )}
              </svg>
              {item.name}
            </li>
          ))}
        </ul>
      )}
      {table ? (
        <div className="chart-table-wrap">
          <table className="table">
            <thead>
              <tr>
                {head.map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={i}>
                  {row.map((cell, j) => (
                    <td key={j}>{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        children
      )}
    </section>
  )
}

export function Tooltip({ x, y, boundary, children }: { x: number; y: number; boundary: number; children: ReactNode }) {
  const flip = x > boundary * 0.6
  return (
    <div
      className="chart-tip"
      role="status"
      style={{ left: x, top: y, transform: flip ? 'translateX(calc(-100% - 14px))' : 'translateX(14px)' }}
    >
      {children}
    </div>
  )
}
