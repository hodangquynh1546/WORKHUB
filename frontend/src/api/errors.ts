import { ApiError } from './client'

const MESSAGES: Record<string, string> = {
  EMAIL_TAKEN: 'Email này đã được đăng ký.',
  INVALID_CREDENTIALS: 'Email hoặc mật khẩu không đúng.',
  INVALID_TOKEN: 'Liên kết không hợp lệ hoặc đã hết hạn.',
  INVALID_REFRESH_TOKEN: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  INVALID_CURRENT_PASSWORD: 'Mật khẩu hiện tại không đúng.',
  PASSWORD_TOO_LONG: 'Mật khẩu quá dài (tối đa 72 byte; ký tự có dấu chiếm nhiều byte hơn).',
  VALIDATION_FAILED: 'Dữ liệu chưa hợp lệ, vui lòng kiểm tra lại.',
  WORKSPACE_NOT_FOUND: 'Không tìm thấy workspace hoặc bạn không phải thành viên.',
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
  CONFLICT: 'Dữ liệu bị trùng, vui lòng thử lại.',
  TEAM_NOT_FOUND: 'Không tìm thấy team.',
  TEAM_NAME_TAKEN: 'Workspace đã có team trùng tên.',
  TEAM_ARCHIVED: 'Team đã được lưu trữ nên không thể thay đổi.',
  MEMBER_NOT_FOUND: 'Người này không còn là thành viên.',
  NOT_WORKSPACE_MEMBER: 'Người này chưa thuộc workspace. Hãy gửi lời mời.',
  ALREADY_MEMBER: 'Người này đã là thành viên.',
  OWNER_PROTECTED: 'Không thể thay đổi hoặc xóa chủ sở hữu workspace.',
  OWNER_CANNOT_LEAVE: 'Chủ sở hữu không thể rời workspace.',
  INVALID_ROLE: 'Vai trò không hợp lệ.',
  EMAIL_REQUIRED: 'Vui lòng nhập email người được mời.',
  INVITATION_NOT_FOUND: 'Không tìm thấy lời mời.',
  INVITATION_INVALID: 'Lời mời đã được sử dụng, bị thu hồi hoặc đã hết hạn.',
  INVITATION_EMAIL_MISMATCH: 'Lời mời này được gửi tới một email khác với tài khoản bạn đang đăng nhập.',
}

const FIELD_LABELS: Record<string, string> = {
  email: 'Email',
  password: 'Mật khẩu',
  newPassword: 'Mật khẩu mới',
  fullName: 'Họ tên',
  name: 'Tên',
  description: 'Mô tả',
  logoUrl: 'Logo URL',
  avatarUrl: 'Ảnh đại diện URL',
}

/** A Vietnamese message for any error thrown by api(). */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const base = (error.code && MESSAGES[error.code]) || error.message
    const fields = Object.keys(error.fieldErrors ?? {}).map((f) => FIELD_LABELS[f] ?? f)
    return fields.length ? `${base} (${fields.join(', ')})` : base
  }
  return 'Không kết nối được tới máy chủ.'
}
