import type { DocumentType } from '../../api/types'

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  PROJECT_CHARTER: 'Project Charter',
  BUSINESS_REQUIREMENTS: 'Business Requirements',
  SRS: 'SRS',
  TECHNICAL_DESIGN: 'Technical Design',
  API_DOCUMENTATION: 'API Documentation',
  MEETING_MINUTES: 'Meeting Minutes',
  SPRINT_PLANNING_NOTES: 'Sprint Planning Notes',
  SPRINT_REVIEW_REPORT: 'Sprint Review Report',
  SPRINT_RETROSPECTIVE_REPORT: 'Sprint Retrospective Report',
  TEST_PLAN: 'Test Plan',
  RELEASE_NOTES: 'Release Notes',
  OTHER: 'Khác',
}

export const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[]

export const formatWhen = (iso: string) => new Date(iso).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
