/** Properties copied from the computed style into the exported SVG, so it looks the same outside the page. */
const PROPS = ['fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'stroke-linecap', 'stroke-linejoin', 'opacity', 'font-family', 'font-size', 'font-weight', 'text-anchor', 'dominant-baseline']

function save(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** A standalone copy of the chart's SVG: styles resolved (CSS variables included), white-or-surface background, legend-free. */
function standalone(svg: SVGSVGElement): { markup: string; width: number; height: number } {
  const copy = svg.cloneNode(true) as SVGSVGElement
  const sources = [svg, ...svg.querySelectorAll('*')]
  const targets = [copy, ...copy.querySelectorAll('*')]
  sources.forEach((source, i) => {
    const style = getComputedStyle(source)
    const target = targets[i] as SVGElement
    target.setAttribute('style', PROPS.map((p) => `${p}:${style.getPropertyValue(p)}`).join(';'))
    target.removeAttribute('class')
    // Presentation attributes may still hold var(--x); the resolved values are in `style`, so drop them.
    for (const attr of ['fill', 'stroke']) if (target.getAttribute(attr)?.includes('var(')) target.removeAttribute(attr)
  })
  const width = svg.width.baseVal.value
  const height = svg.height.baseVal.value
  copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  copy.setAttribute('viewBox', `0 0 ${width} ${height}`)
  const background = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
  background.setAttribute('width', String(width))
  background.setAttribute('height', String(height))
  background.setAttribute('fill', getComputedStyle(document.body).getPropertyValue('--surface') || '#ffffff')
  copy.insertBefore(background, copy.firstChild)
  return { markup: new XMLSerializer().serializeToString(copy), width, height }
}

/** Saves the chart inside `root` as SVG, or as PNG at 2x for sharp slides and documents. */
export async function exportChart(root: HTMLElement, format: 'svg' | 'png', name: string): Promise<void> {
  const svg = root.querySelector<SVGSVGElement>('.chart svg[role="img"]')
  if (!svg) throw new Error('Không có biểu đồ để xuất (đang xem dạng bảng?).')
  const { markup, width, height } = standalone(svg)
  const file = `${name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'chart'}`
  if (format === 'svg') {
    save(new Blob([markup], { type: 'image/svg+xml;charset=utf-8' }), `${file}.svg`)
    return
  }
  const image = new Image()
  const url = URL.createObjectURL(new Blob([markup], { type: 'image/svg+xml;charset=utf-8' }))
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve()
    image.onerror = () => reject(new Error('Không tạo được ảnh PNG.'))
    image.src = url
  })
  const canvas = document.createElement('canvas')
  canvas.width = width * 2
  canvas.height = height * 2
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Trình duyệt không hỗ trợ canvas.')
  ctx.scale(2, 2)
  ctx.drawImage(image, 0, 0, width, height)
  URL.revokeObjectURL(url)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
  if (!blob) throw new Error('Không tạo được ảnh PNG.')
  save(blob, `${file}.png`)
}
