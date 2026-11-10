import { describe, expect, it } from 'vitest'
import { formatDay, invitationRoleLabel, scheduleProgress } from './roles'

describe('formatDay', () => {
  it('shows yyyy-mm-dd as dd/mm/yyyy and a dash for nothing', () => {
    expect(formatDay('2026-03-09')).toBe('09/03/2026')
    expect(formatDay(null)).toBe('—')
  })
})

describe('scheduleProgress', () => {
  const today = new Date(2026, 2, 5) // 5 March 2026

  it('is null without both dates', () => {
    expect(scheduleProgress(null, '2026-03-10', today)).toBeNull()
    expect(scheduleProgress('2026-03-01', null, today)).toBeNull()
  })

  it('counts calendar days inclusive of both ends', () => {
    expect(scheduleProgress('2026-03-01', '2026-03-10', today)).toMatchObject({
      totalDays: 10,
      elapsedDays: 5,
      remainingDays: 5,
      percent: 50,
      notStarted: false,
      finished: false,
    })
  })

  it('flags not started and finished schedules', () => {
    expect(scheduleProgress('2026-03-08', '2026-03-10', today)).toMatchObject({ elapsedDays: 0, notStarted: true })
    expect(scheduleProgress('2026-02-01', '2026-02-10', today)).toMatchObject({ remainingDays: 0, finished: true, percent: 100 })
  })
})

describe('invitationRoleLabel', () => {
  it('reads the role as a project, team or workspace role depending on the invitation', () => {
    expect(invitationRoleLabel(null, 'QA_ENGINEER', 'p1')).toBe('QA Engineer')
    expect(invitationRoleLabel('t1', 'LEAD')).not.toBe('LEAD')
    expect(invitationRoleLabel(null, 'ADMIN')).not.toBe('ADMIN')
  })
})
