import { describe, expect, it } from 'vitest'
import { AUDIT_ACTIONS, auditActionLabel, auditDetail } from './auditLabels'

describe('audit labels', () => {
  it('names every recorded action in Vietnamese, including deletions and ownership transfer', () => {
    for (const code of ['workspace.delete', 'team.delete', 'account.delete', 'workspace.ownership_transfer']) {
      expect(AUDIT_ACTIONS[code]).toBeTruthy()
    }
    expect(auditActionLabel('something.new')).toBe('something.new')
  })

  it('turns key=value details into readable text', () => {
    expect(auditDetail('role=ADMIN; email=a@b.c')).toBe('Vai trò: Quản trị viên · Email: a@b.c')
    expect(auditDetail('name=Doomed')).toBe('Tên: Doomed')
    expect(auditDetail('kind=LINK')).toBe('Loại: Liên kết mời')
  })
})
