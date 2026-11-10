import type { NotificationType } from '../api/types'

/** What each notification type is called in the settings, and what it tells the user. */
export const NOTIFICATION_LABELS: Record<NotificationType, { name: string; hint: string }> = {
  ISSUE_ASSIGNED: { name: 'Được giao công việc', hint: 'Khi ai đó giao công việc cho bạn' },
  ISSUE_STATUS_CHANGED: { name: 'Công việc đổi trạng thái', hint: 'Công việc bạn giao hoặc báo cáo được chuyển cột' },
  ISSUE_COMMENTED: { name: 'Bình luận mới', hint: 'Có bình luận ở công việc bạn phụ trách hoặc báo cáo' },
  MENTIONED: { name: 'Được nhắc đến', hint: 'Ai đó gõ @tên bạn trong bình luận hoặc chat' },
  INVITED_TO_TEAM: { name: 'Được thêm vào team', hint: 'Khi bạn được thêm vào một team' },
  INVITED_TO_PROJECT: { name: 'Được thêm vào dự án', hint: 'Khi bạn được thêm vào một dự án' },
  MEETING_INVITED: { name: 'Được mời họp', hint: 'Khi bạn được thêm vào danh sách tham dự' },
  MEETING_CANCELLED: { name: 'Cuộc họp bị hủy', hint: 'Cuộc họp bạn tham dự bị hủy' },
  MEETING_REMINDER: { name: 'Nhắc giờ họp', hint: 'Trước giờ họp theo thời gian nhắc đã đặt' },
  SPRINT_STARTED: { name: 'Sprint bắt đầu', hint: 'Sprint của dự án bạn tham gia bắt đầu' },
  SPRINT_COMPLETED: { name: 'Sprint kết thúc', hint: 'Sprint của dự án bạn tham gia kết thúc' },
  TASK_BLOCKED: { name: 'Công việc bị chặn', hint: 'Việc bạn phụ trách, hoặc cả dự án nếu bạn là quản lý, bị đánh dấu Blocked' },
  ALERT_OVERDUE: { name: 'Quá hạn', hint: 'Công việc đã quá hạn' },
  ALERT_DUE_SOON: { name: 'Sắp đến hạn', hint: 'Công việc sắp đến hạn' },
  ALERT_SPRINT_AT_RISK: { name: 'Sprint có nguy cơ', hint: 'Sprint còn ít ngày mà nhiều việc chưa xong (dành cho người điều hành)' },
  ALERT_SCOPE_CHANGE: { name: 'Phạm vi sprint đổi', hint: 'Sprint thêm nhiều việc sau khi bắt đầu (dành cho người điều hành)' },
  ALERT_OVERLOAD: { name: 'Quá tải', hint: 'Một thành viên đang giữ quá nhiều việc' },
  ALERT_NO_PROGRESS: { name: 'Không có tiến triển', hint: 'Việc đang làm lâu không được cập nhật' },
}

/** "5 phút trước", "hôm qua", or the date for older ones. */
export function timeAgo(iso: string, now = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return 'vừa xong'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} phút trước`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} giờ trước`
  const days = Math.round(hours / 24)
  if (days < 7) return `${days} ngày trước`
  return new Date(iso).toLocaleDateString('vi-VN')
}
