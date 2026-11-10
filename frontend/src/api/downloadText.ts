/** Saves text the browser already has (a document, minutes) as a file, with no server round trip. */
export function downloadText(fileName: string, text: string, type = 'text/markdown;charset=utf-8') {
  const safe = fileName.replace(/[\\/:*?"<>|]+/g, ' ').trim() || 'workhub'
  const url = URL.createObjectURL(new Blob([text], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = safe
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
