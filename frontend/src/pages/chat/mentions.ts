import type { UserRef } from '../../api/types'

/** The ids of the people whose `@Full Name` still appears in the text (longest names first so "An" does not eat "Anh"). */
export function extractMentions(text: string, people: UserRef[], selfId?: string): string[] {
  const lower = text.toLowerCase()
  return [...people]
    .sort((a, b) => b.fullName.length - a.fullName.length)
    .filter((p) => p.id !== selfId && lower.includes(`@${p.fullName.toLowerCase()}`))
    .map((p) => p.id)
}

/** The `@partial` word being typed right before the caret, or null. */
export function mentionQuery(text: string, caret: number): { start: number; query: string } | null {
  const before = text.slice(0, caret)
  const match = /(^|\s)@([^\s@]{0,30})$/.exec(before)
  return match ? { start: before.length - match[2].length - 1, query: match[2].toLowerCase() } : null
}
