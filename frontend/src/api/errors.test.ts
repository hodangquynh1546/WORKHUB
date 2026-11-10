import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { errorMessage } from './errors'

describe('errorMessage', () => {
  it('maps a known code to Vietnamese text', () => {
    expect(errorMessage(new ApiError(429, 'Too many', 'RATE_LIMITED'))).toContain('quá nhanh')
  })

  it('falls back to the server message and names the invalid fields', () => {
    expect(errorMessage(new ApiError(400, 'Invalid', 'WHATEVER', { title: 'required' }))).toBe('Invalid (Tiêu đề)')
  })

  it('says the server is unreachable for anything else', () => {
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe('Không kết nối được tới máy chủ.')
  })
})
