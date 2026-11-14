import type { ReactNode } from 'react'
import { MermaidDiagram } from './MermaidDiagram'

/**
 * A small, safe Markdown renderer: headings, paragraphs, lists, quotes, code, tables, rules, **bold**, *italic*,
 * `code` and http(s)/mailto links. It builds React elements (never raw HTML), so document text cannot inject markup.
 */
type Block =
  | { t: 'h'; level: number; text: string }
  | { t: 'p'; text: string }
  | { t: 'list'; ordered: boolean; items: string[] }
  | { t: 'quote'; text: string }
  | { t: 'code'; lang: string; text: string }
  | { t: 'hr' }
  | { t: 'table'; head: string[]; rows: string[][] }

const cells = (line: string) =>
  line
    .trim()
    .replace(/^\||\|$/g, '')
    .split(/(?<!\\)\|/)
    .map((c) => c.replace(/\\\|/g, '|').trim())

function parse(source: string): Block[] {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const blocks: Block[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) {
      i++
    } else if (line.startsWith('```')) {
      const lang = line.slice(3).trim().toLowerCase()
      const body: string[] = []
      i++
      while (i < lines.length && !lines[i].startsWith('```')) body.push(lines[i++])
      i++
      blocks.push({ t: 'code', lang, text: body.join('\n') })
    } else if (/^#{1,4}\s/.test(line)) {
      const level = line.match(/^#+/)![0].length
      blocks.push({ t: 'h', level, text: line.replace(/^#+\s+/, '').trim() })
      i++
    } else if (/^(-{3,}|\*{3,})\s*$/.test(line)) {
      blocks.push({ t: 'hr' })
      i++
    } else if (line.startsWith('>')) {
      const body: string[] = []
      while (i < lines.length && lines[i].startsWith('>')) body.push(lines[i++].replace(/^>\s?/, ''))
      blocks.push({ t: 'quote', text: body.join(' ') })
    } else if (/^\s*([-*]|\d+\.)\s+/.test(line)) {
      const ordered = /^\s*\d+\./.test(line)
      const items: string[] = []
      while (i < lines.length && /^\s*([-*]|\d+\.)\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*([-*]|\d+\.)\s+/, ''))
      blocks.push({ t: 'list', ordered, items })
    } else if (line.includes('|') && /^\s*\|?\s*:?-{2,}/.test(lines[i + 1] ?? '')) {
      const head = cells(line)
      const rows: string[][] = []
      i += 2
      while (i < lines.length && lines[i].includes('|')) rows.push(cells(lines[i++]))
      blocks.push({ t: 'table', head, rows })
    } else {
      const body: string[] = []
      while (i < lines.length && lines[i].trim() && !/^(#{1,4}\s|```|>|\s*([-*]|\d+\.)\s)/.test(lines[i])) body.push(lines[i++])
      blocks.push({ t: 'p', text: body.join(' ') })
    }
  }
  return blocks
}

const INLINE = /(`[^`]+`)|(\*\*[^*]+\*\*)|(\*[^*\s][^*]*\*)|(\[[^\]]+\]\([^)\s]+\))/g

function inline(text: string): ReactNode[] {
  const out: ReactNode[] = []
  let last = 0
  for (const match of text.matchAll(INLINE)) {
    const at = match.index
    if (at > last) out.push(text.slice(last, at))
    const token = match[0]
    if (token.startsWith('`')) out.push(<code key={at}>{token.slice(1, -1)}</code>)
    else if (token.startsWith('**')) out.push(<strong key={at}>{inline(token.slice(2, -2))}</strong>)
    else if (token.startsWith('*')) out.push(<em key={at}>{inline(token.slice(1, -1))}</em>)
    else {
      const [, label, url] = token.match(/^\[([^\]]+)\]\(([^)\s]+)\)$/)!
      out.push(
        /^(https?:\/\/|mailto:)/i.test(url) ? (
          <a key={at} href={url} target="_blank" rel="noopener noreferrer">
            {label}
          </a>
        ) : (
          token
        ),
      )
    }
    last = at + token.length
  }
  if (last < text.length) out.push(text.slice(last))
  return out
}

export function Markdown({ source }: { source: string }) {
  const blocks = parse(source)
  if (blocks.length === 0) return <p className="muted">Tài liệu chưa có nội dung.</p>
  return (
    <div className="markdown">
      {blocks.map((b, i) => {
        switch (b.t) {
          case 'h': {
            const Tag = `h${Math.min(6, b.level + 1)}` as 'h2'
            return <Tag key={i}>{inline(b.text)}</Tag>
          }
          case 'p':
            return <p key={i}>{inline(b.text)}</p>
          case 'quote':
            return <blockquote key={i}>{inline(b.text)}</blockquote>
          case 'code':
            if (b.lang === 'mermaid') return <MermaidDiagram key={`${i}:${b.text}`} source={b.text} />
            return (
              <pre key={i}>
                <code>{b.text}</code>
              </pre>
            )
          case 'hr':
            return <hr key={i} />
          case 'list': {
            const Tag = b.ordered ? 'ol' : 'ul'
            return (
              <Tag key={i}>
                {b.items.map((item, j) => (
                  <li key={j}>{inline(item)}</li>
                ))}
              </Tag>
            )
          }
          case 'table':
            return (
              <div key={i} className="markdown-table">
                <table>
                  <thead>
                    <tr>
                      {b.head.map((h, j) => (
                        <th key={j}>{inline(h)}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {b.rows.map((row, r) => (
                      <tr key={r}>
                        {row.map((c, j) => (
                          <td key={j}>{inline(c)}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
        }
      })}
    </div>
  )
}
