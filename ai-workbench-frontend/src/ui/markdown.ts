import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ gfm: true, breaks: true })

export function renderMarkdown(source: string): string {
  if (!source.trim()) return ''
  const html = marked.parse(source, { async: false })
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}
