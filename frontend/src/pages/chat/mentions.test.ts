import { describe, expect, it } from 'vitest'
import type { UserRef } from '../../api/types'
import { extractMentions, mentionQuery } from './mentions'

const person = (id: string, fullName: string) => ({ id, fullName, avatarUrl: null }) as UserRef

describe('extractMentions', () => {
  const people = [person('1', 'An'), person('2', 'Anh Tuấn'), person('3', 'Bình')]

  it('finds the people whose name is still in the text, case-insensitively', () => {
    expect(extractMentions('chào @bình nhé', people)).toEqual(['3'])
  })

  it('does not let a short name match inside a longer one twice', () => {
    expect(extractMentions('@Anh Tuấn xem giúp', people)).toEqual(['2', '1'])
  })

  it('skips the sender and names that were deleted from the text', () => {
    expect(extractMentions('@An @Bình', people, '1')).toEqual(['3'])
    expect(extractMentions('không nhắc ai', people)).toEqual([])
  })
})

describe('mentionQuery', () => {
  it('returns the partial word right before the caret', () => {
    expect(mentionQuery('hi @Bi', 6)).toEqual({ start: 3, query: 'bi' })
    expect(mentionQuery('@', 1)).toEqual({ start: 0, query: '' })
  })

  it('is null when no mention is being typed', () => {
    expect(mentionQuery('email a@b.com', 13)).toBeNull()
    expect(mentionQuery('hello world', 11)).toBeNull()
  })
})
