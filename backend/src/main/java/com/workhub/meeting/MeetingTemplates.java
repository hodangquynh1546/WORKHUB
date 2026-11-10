package com.workhub.meeting;

import java.util.List;

/**
 * Starting points for each meeting type: a default length, an agenda and an outline for the minutes. They are
 * suggestions the organizer edits (the Scrum Guide does not prescribe the Daily Scrum questions, for example).
 */
public final class MeetingTemplates {

	public record Template(MeetingType type, String name, String description, int durationMinutes, String agenda,
			String minutes) {
	}

	private static final List<Template> ALL = List.of(
			new Template(MeetingType.PROJECT_KICKOFF, "Project Kickoff", "Khởi động dự án", 60,
					"1. Mục tiêu và phạm vi dự án\n2. Các bên liên quan và vai trò\n3. Kế hoạch tổng quan và mốc quan trọng\n4. Rủi ro ban đầu\n5. Cách làm việc và giao tiếp",
					"## Quyết định\n\n- \n\n## Phạm vi và mục tiêu đã thống nhất\n\n\n## Rủi ro đã nêu\n\n- \n\n## Việc cần làm tiếp\n\n- "),
			new Template(MeetingType.SPRINT_PLANNING, "Sprint Planning", "Lập kế hoạch Sprint", 120,
					"1. Sprint Goal: vì sao sprint này có giá trị\n2. Capacity của team trong sprint\n3. Chọn các Backlog Item và ước lượng Story Points\n4. Chia nhỏ thành công việc, xác định người phụ trách",
					"## Sprint Goal\n\n\n## Capacity của team\n\n\n## Backlog Items đã chọn và Story Points\n\n- \n\n## Sprint Backlog\n\n\n## Kết luận và người phụ trách\n\n- "),
			new Template(MeetingType.DAILY_SCRUM, "Daily Scrum", "Đồng bộ tiến độ hằng ngày", 15,
					"Gợi ý (không bắt buộc):\n- Hôm qua đã xong gì?\n- Hôm nay dự định làm gì?\n- Có vướng mắc nào không?",
					"## Việc đã xong\n\n- \n\n## Kế hoạch tiếp theo\n\n- \n\n## Impediments / Blockers\n\n- \n\n## Người cần hỗ trợ\n\n- "),
			new Template(MeetingType.BACKLOG_REFINEMENT, "Backlog Refinement", "Làm rõ và ưu tiên Backlog", 60,
					"1. Các Backlog Item cần làm rõ\n2. Tiêu chí chấp nhận\n3. Ước lượng Story Points\n4. Sắp xếp lại độ ưu tiên",
					"## Các mục đã làm rõ\n\n- \n\n## Thay đổi về ước lượng và ưu tiên\n\n- \n\n## Câu hỏi còn mở\n\n- "),
			new Template(MeetingType.SPRINT_REVIEW, "Sprint Review", "Đánh giá Increment", 60,
					"1. Sprint Goal có đạt không\n2. Demo Increment đã hoàn thành\n3. Phản hồi từ Stakeholders\n4. Thay đổi tiếp theo cho Product Backlog",
					"## Sprint Goal đã đạt chưa\n\n\n## Increment đã hoàn thành\n\n- \n\n## Phản hồi từ Stakeholders\n\n- \n\n## Thay đổi cho Product Backlog\n\n- "),
			new Template(MeetingType.SPRINT_RETROSPECTIVE, "Sprint Retrospective", "Cải tiến cách làm việc", 60,
					"1. Điều gì đã tốt\n2. Điều gì chưa tốt\n3. Có thể cải thiện gì\n4. Chốt Action Items, người phụ trách và hạn",
					"## What went well\n\n- \n\n## What didn't go well\n\n- \n\n## What can be improved\n\n- \n\n## Action Items\n\nThêm từng việc ở mục Action Items bên dưới để gán người phụ trách, hạn và theo dõi trạng thái."),
			new Template(MeetingType.GENERAL, "General Meeting", "Cuộc họp thông thường", 30, "1. \n2. \n3. ",
					"## Nội dung chính\n\n\n## Quyết định\n\n- \n\n## Vấn đề phát sinh\n\n- "),
			new Template(MeetingType.TECHNICAL, "Technical Meeting", "Thảo luận kỹ thuật", 60,
					"1. Vấn đề cần giải quyết\n2. Các phương án\n3. Đánh đổi và rủi ro\n4. Quyết định",
					"## Vấn đề\n\n\n## Các phương án\n\n- \n\n## Quyết định kỹ thuật\n\n\n## Việc cần làm\n\n- "));

	private MeetingTemplates() {
	}

	public static List<Template> all() {
		return ALL;
	}

}
