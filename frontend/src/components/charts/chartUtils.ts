import { useEffect, useRef, useState } from 'react'

export type Tone = 'ink' | 'mid' | 'soft'

export const toneColor = (tone: Tone) => `var(--chart-${tone})`

/** Width of the element the ref is attached to; charts are drawn at real pixel size so text is never scaled. */
export function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.round(entry.contentRect.width)))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])
  return [ref, width] as const
}

/** A round top value and about four evenly spaced ticks (whole numbers when `integer`). */
export function niceScale(max: number, integer = true) {
  if (!(max > 0)) return { top: integer ? 4 : 1, ticks: integer ? [0, 1, 2, 3, 4] : [0, 0.25, 0.5, 0.75, 1] }
  const rough = max / 4
  const pow = 10 ** Math.floor(Math.log10(rough))
  let step = [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= rough) ?? rough
  if (integer) step = Math.max(1, Math.ceil(step))
  const top = Math.ceil(max / step) * step
  const ticks: number[] = []
  for (let v = 0; v <= top + step / 1000; v += step) ticks.push(Math.round(v * 100) / 100)
  return { top, ticks }
}

export const fmt = (value: number) => (Number.isInteger(value) ? String(value) : value.toFixed(1))

/** Path for one line; a null value breaks the line so missing days are never drawn as zero. */
export function linePath(values: (number | null)[], x: (i: number) => number, y: (v: number) => number) {
  let d = ''
  let open = false
  values.forEach((v, i) => {
    if (v == null) {
      open = false
      return
    }
    d += `${open ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`
    open = true
  })
  return d
}

/** Path with rounded top corners and a square bottom, for columns growing from the baseline. */
export function columnPath(x: number, y: number, w: number, h: number, radius = 4) {
  const r = Math.min(radius, w / 2, h)
  return `M${x},${y + h}V${y + r}Q${x},${y} ${x + r},${y}H${x + w - r}Q${x + w},${y} ${x + w},${y + r}V${y + h}Z`
}
