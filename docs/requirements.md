# WORKHUB – Agile Project Management Platform

Đặc tả nghiệp vụ tổng thể. Dùng làm nền tảng để phân tích yêu cầu, thiết kế database, API và triển khai.

Luồng chính: đăng ký tài khoản → tạo Workspace/Team → mời thành viên → tạo và quản lý Project → quản lý Task và tài liệu → lập kế hoạch Sprint → tổ chức các cuộc họp Scrum → theo dõi tiến độ bằng biểu đồ Agile → trao đổi realtime và nhận cảnh báo.

## 1. Tổng quan

WORKHUB hỗ trợ cá nhân và nhóm phát triển phần mềm quản lý toàn bộ vòng đời dự án theo Agile/Scrum.

Mục tiêu:
- Quản lý thành viên, nhóm và phân quyền.
- Quản lý nhiều dự án trong một Team.
- Quản lý Product Backlog, Task, Bug, User Story, Epic.
- Phân công, theo dõi và đánh giá công việc.
- Tạo Sprint, lập Sprint Backlog và theo dõi Sprint.
- Quản lý tài liệu dự án và cộng tác chỉnh sửa đồng bộ.
- Lên lịch, tổ chức cuộc họp và dùng mẫu cuộc họp Scrum.
- Thống kê tiến độ bằng Burndown, Burnup và các biểu đồ Agile.
- Gửi thông báo, cảnh báo và nhắn tin realtime.
- Xuất báo cáo Excel, PDF và các định dạng phù hợp.

## 2. Cấu trúc tổ chức

```
WORKHUB PLATFORM
└── User (đăng ký, đăng nhập)
    └── Workspace (không gian của cá nhân hoặc tổ chức)
        └── Team (thành viên và quyền hạn riêng)
            └── Project A, Project B, ...
                ├── Backlog & Issues
                ├── Sprint & Board
                ├── Documents
                ├── Meetings
                ├── Reports & Charts
                └── Chat & Notifications
```

### 2.1. User – Tài khoản

- Đăng ký bằng email và mật khẩu; đăng nhập, đăng xuất.
- Xác thực email; quên và đặt lại mật khẩu.
- Đăng nhập bằng Google (có thể làm sau).
- Cập nhật hồ sơ, ảnh đại diện, thông tin cá nhân.
- Đổi mật khẩu và thiết lập bảo mật.
- Tham gia nhiều Team hoặc Workspace.
- Nhận thông báo từ các dự án mình tham gia.

### 2.2. Workspace

- Tạo Workspace; đặt tên, mô tả, logo.
- Mời người khác tham gia; quản lý thành viên; phân quyền quản trị.
- Quản lý các Team thuộc Workspace.
- Theo dõi tổng quan các Project.
- Cấu hình thông báo và thiết lập chung.

### 2.3. Team

Một Workspace có nhiều Team (ví dụ Backend, Frontend, Mobile, QA, Design). Một người có thể tham gia nhiều Team.

- Tạo, chỉnh sửa, lưu trữ Team.
- Mời thành viên bằng email hoặc liên kết mời; chấp nhận hoặc từ chối lời mời.
- Xóa thành viên; quản lý vai trò thành viên.
- Xem danh sách Project và hoạt động gần đây của Team.

### 2.4. Vai trò và phân quyền

| Vai trò | Quyền hạn chính |
|---|---|
| Workspace Owner | Quản lý toàn bộ Workspace |
| Workspace Admin | Quản lý thành viên và cấu hình |
| Project Manager | Quản lý Project và kế hoạch |
| Product Owner | Quản lý Product Backlog, ưu tiên công việc |
| Scrum Master | Điều phối Scrum, hỗ trợ Sprint |
| Developer | Thực hiện Task, cập nhật tiến độ |
| QA Engineer | Kiểm thử, quản lý Bug |
| Stakeholder | Theo dõi, xem báo cáo, đóng góp ý kiến |

Product Owner, Scrum Master và Developer là trách nhiệm trong Scrum Team. Không khóa cứng một người vào một vai trò toàn hệ thống: phân quyền được gán theo phạm vi Workspace, Team và Project.

## 3. Project Management (module trung tâm)

Mỗi Team có thể tạo nhiều Project.

### 3.1. Tạo Project

Thông tin: tên, mã Project (ví dụ `ECOM`), mô tả, ảnh đại diện, ngày bắt đầu và kết thúc dự kiến, Project Manager, Product Owner, Scrum Master, thành viên, phương pháp (Scrum hoặc Kanban), trạng thái (Planning, Active, On Hold, Completed, Archived).

Sau khi tạo, hệ thống tự khởi tạo các khu vực: Overview, Backlog, Sprint, Board, Documents, Meetings, Calendar, Reports, Project Settings.

### 3.2. Project Overview

Hiển thị: tổng số Task, đã hoàn thành, đang thực hiện, chưa bắt đầu, quá hạn; tổng Story Points; Sprint hiện tại và tiến độ; thành viên; hoạt động gần đây; các mốc thời gian quan trọng; cảnh báo và vấn đề đang tồn tại.

## 4. Task & Issue Management

### 4.1. Loại công việc

| Loại | Ý nghĩa |
|---|---|
| Epic | Nhóm chức năng lớn |
| Feature | Tính năng |
| User Story | Nhu cầu từ góc nhìn người dùng |
| Task | Công việc cần thực hiện |
| Sub-task | Công việc con |
| Bug | Lỗi phần mềm |
| Spike | Nghiên cứu, thử nghiệm |

Không phải loại nào cũng là thành phần bắt buộc của Scrum; đây là các loại Issue giúp quản lý thực tế linh hoạt hơn.

### 4.2. Thông tin Task

Task ID tự động, tiêu đề, mô tả, loại, Project, Parent Task/Epic, người tạo, người được giao, Priority (Low, Medium, High, Critical), Status, Start Date, Due Date, Story Points, Original Estimate, Remaining Estimate, Acceptance Criteria, Labels, Attachments, Checklist, Related Issues.

### 4.3. Phân công

Người có quyền có thể: giao Task, đổi người phụ trách, theo dõi khối lượng từng người, đặt thời hạn, theo dõi Task quá hạn, gửi thông báo khi giao Task, theo dõi lịch sử đổi người phụ trách.

### 4.4. Trang chi tiết Task

Thông tin, trạng thái, người phụ trách, tiến độ, bình luận, file đính kèm, lịch sử hoạt động, công việc liên quan, checklist, Work Log, người theo dõi.

### 4.5. Cách hiển thị

List, Kanban Board, Table, Calendar, Timeline, Gantt (nếu cần quản lý phụ thuộc và thời gian).

## 5. Product Backlog

- Tạo Epic, Feature, User Story, Task, Bug.
- Sắp xếp ưu tiên, kéo thả đổi thứ tự.
- Gán Story Points, Priority, người thực hiện dự kiến.
- Quản lý Acceptance Criteria.
- Tìm kiếm, lọc, gom nhóm theo Epic.
- Chuyển Issue vào Sprint.
- Theo dõi trạng thái Refinement.
- Lưu lịch sử thay đổi Backlog.

Product Backlog là danh sách được ưu tiên liên tục, không phải danh sách Task chỉ dùng cho một Sprint.

## 6. Sprint Management

### 6.1. Tạo Sprint

Thông tin: tên, Sprint Goal, Start Date, End Date, thời lượng, Scrum Team, Sprint Owner/Facilitator, Sprint Backlog, trạng thái.

Trạng thái: `Future → Active → Completed`, và có thể `Cancelled`.

### 6.2. Sprint Planning

1. Chọn Sprint cần lập kế hoạch.
2. Xác định Sprint Goal.
3. Xem Product Backlog đã ưu tiên.
4. Chọn các Issue phù hợp.
5. Thảo luận năng lực và khối lượng của Team.
6. Ước lượng Story Points.
7. Xây dựng Sprint Backlog.
8. Xác nhận Sprint Plan.
9. Bắt đầu Sprint.

Sprint Backlog phải lưu: danh sách Issue tại thời điểm bắt đầu, Sprint Goal, tổng Story Points ban đầu, thành viên thực hiện, thay đổi Issue trong Sprint, lịch sử thêm/xóa Task, trạng thái công việc theo từng ngày. Đây là điều kiện để báo cáo Sprint chính xác.

### 6.3. Sprint Board

Cột mặc định: To Do, In Progress, Code Review, Testing, Done. Workflow cấu hình được theo từng Project.

Chức năng: kéo thả Task giữa các cột, đổi Assignee, theo dõi tiến độ, hiển thị Priority, Story Points, Due Date, đánh dấu Blocked, lọc theo người thực hiện/loại/trạng thái, lịch sử chuyển trạng thái.

### 6.4. Sprint Dashboard

Thời gian đã trôi qua và còn lại; tổng Story Points, đã hoàn thành, còn lại; số Task hoàn thành, đang làm, Blocked, quá hạn; tiến độ Sprint Goal; thay đổi Sprint Backlog.

Tiến độ công việc khác với việc đạt Sprint Goal: hoàn thành nhiều Task chưa chắc đã đạt mục tiêu Sprint.

### 6.5. Hoàn thành Sprint

- Tổng hợp Issue Done và chưa hoàn thành.
- Ghi nhận kết quả Sprint Goal; xem Sprint Report.
- Issue chưa xong được đưa về Product Backlog hoặc sang Sprint sau theo quyết định của Product Owner.
- Lưu lịch sử Sprint để phân tích sau.

## 7. Agile Analytics

### 7.1. Burndown Chart

Lượng công việc còn lại theo thời gian trong Sprint.
- Trục X: ngày trong Sprint. Trục Y: Work Remaining (Story Points hoặc số Task).
- Ideal Line: tiến độ lý tưởng. Actual Line: công việc thực tế còn lại.
- `RemainingWork(d) = TotalScope(d) − CompletedWork(d)`

Ví dụ: Sprint 40 Story Points, 10 ngày làm việc.

Cách đọc: Actual trên Ideal là chậm hơn kế hoạch; dưới Ideal là nhanh hơn; bằng 0 là khối lượng được ghi nhận đã hoàn thành.

Hệ thống phải lưu snapshot theo ngày hoặc theo từng lần cập nhật. Chỉ lấy trạng thái hiện tại của Task thì không thể tái dựng biểu đồ lịch sử.

### 7.2. Burnup Chart

Hai đường: Completed Work và Total Scope. Ưu điểm: thấy lượng việc đã xong, theo dõi thay đổi phạm vi, phân biệt tiến độ với việc tăng/giảm Scope, hữu ích khi Backlog thay đổi trong Sprint.

### 7.3. Các biểu đồ khác

| Biểu đồ | Mục đích |
|---|---|
| Velocity Chart | Story Points hoàn thành qua các Sprint |
| Cumulative Flow Diagram | Lượng Issue ở từng trạng thái |
| Sprint Burndown | Công việc còn lại |
| Sprint Burnup | Công việc hoàn thành và Scope |
| Epic Burndown | Tiến độ Epic |
| Release Burndown | Tiến độ Release |
| Lead Time | Từ lúc yêu cầu xuất hiện đến khi hoàn thành |
| Cycle Time | Từ lúc bắt đầu xử lý đến khi hoàn thành |
| Workload Chart | Khối lượng công việc từng thành viên |
| Bug Trend | Xu hướng phát sinh và xử lý lỗi |

Velocity chỉ dùng để dự báo năng lực của cùng một Team, không dùng để so sánh năng suất giữa các Team.

## 8. Document Management

### 8.1. Quản lý tài liệu (theo từng Project)

Tạo, sửa, xóa hoặc lưu trữ; tổ chức thư mục; phân loại; tìm kiếm nội dung; đính kèm file; gắn tài liệu vào Task, Sprint hoặc Meeting; quyền xem và sửa.

Loại tài liệu phổ biến: Project Charter, Business Requirements, SRS, Technical Design, API Documentation, Meeting Minutes, Sprint Planning Notes, Sprint Review Report, Sprint Retrospective Report, Test Plan, Release Notes.

### 8.2. Chỉnh sửa đồng bộ realtime (nâng cao)

Cùng mở và sửa đồng thời; thấy con trỏ người khác; biết ai đang sửa; nhận thay đổi gần như tức thì; bình luận và mention; lịch sử phiên bản; khôi phục phiên bản cũ.

Không dùng WebSocket để gửi toàn bộ nội dung rồi ghi đè liên tục. Cần cơ chế xử lý xung đột như Yjs/CRDT hoặc OT, kết hợp lưu phiên bản và đồng bộ server.

## 9. Meeting Management

Tích hợp với Project Calendar và Sprint.

### 9.1. Tạo cuộc họp

Tiêu đề, loại, Project, Sprint liên quan, người tổ chức, người tham dự, thời gian bắt đầu/kết thúc, địa điểm hoặc link họp trực tuyến, Agenda, mô tả, tài liệu đính kèm, nhắc nhở trước giờ họp.

### 9.2. Mẫu cuộc họp

| Mẫu | Mục đích |
|---|---|
| Project Kickoff | Khởi động dự án |
| Sprint Planning | Lập kế hoạch Sprint |
| Daily Scrum | Đồng bộ tiến độ hằng ngày |
| Backlog Refinement | Làm rõ và ưu tiên Backlog |
| Sprint Review | Đánh giá Increment |
| Sprint Retrospective | Cải tiến cách làm việc |
| General Meeting | Cuộc họp thông thường |
| Technical Meeting | Thảo luận kỹ thuật |

### 9.3. Nội dung từng mẫu

- **Sprint Planning:** Sprint Goal, Capacity của Team, Backlog Items, Story Points, Sprint Backlog, kết luận và người phụ trách.
- **Daily Scrum:** việc đã xong, kế hoạch tiếp theo, Impediments/Blockers, người cần hỗ trợ. Ba câu hỏi quen thuộc là template gợi ý, không bắt buộc (Scrum Guide không quy định).
- **Sprint Review:** Sprint Goal có đạt không, Increment đã hoàn thành, demo, feedback từ Stakeholders, thay đổi tiếp theo cho Product Backlog.
- **Sprint Retrospective:** What went well, What didn't go well, What can be improved, Action Items, người phụ trách, deadline, trạng thái thực hiện.

### 9.4. Meeting Minutes

Ghi biên bản, quyết định, vấn đề phát sinh; tạo Action Item; giao Task từ nội dung họp; chia sẻ biên bản; xuất PDF.

## 10. Calendar & Scheduling

Project Calendar, Team Calendar, Sprint Timeline, lịch họp, Due Date của Task, ngày Release, Milestones, nhắc lịch, lọc theo Project hoặc thành viên, đồng bộ lịch ngoài (sau này). Hỗ trợ Calendar View và Timeline View.

## 11. Communication

### 11.1. Chat

Chat 1-1, nhóm, theo Team, theo Project. Gửi, sửa/xóa theo quyền, reply, reaction, mention, gửi ảnh và file, trạng thái đã đọc, tìm kiếm tin nhắn.

### 11.2. Realtime

Tin nhắn tức thì; cập nhật Task và Board không cần tải lại; trạng thái thành viên; đồng bộ tài liệu; thông báo khi bị mention; số tin chưa đọc.

### 11.3. Notification Center

Thông báo khi: được giao Task, Task bị thay đổi, sắp đến hạn, quá hạn, có bình luận, có mention, Sprint sắp bắt đầu/kết thúc, Meeting sắp diễn ra, được mời vào Team/Project, có tài liệu được chia sẻ, có thay đổi quan trọng trong Project.

Kênh: trong ứng dụng và email; Push Notification làm sau.

## 12. Progress Alert

| Cảnh báo | Điều kiện |
|---|---|
| Task Overdue | Task đã quá Due Date |
| Task Due Soon | Task sắp đến hạn |
| Sprint At Risk | Tiến độ thực tế có dấu hiệu không đạt kế hoạch |
| Sprint Goal At Risk | Nguy cơ không đạt Sprint Goal |
| Blocked Task | Task bị đánh dấu Blocked |
| Workload Overload | Thành viên có khối lượng quá cao |
| Scope Change | Sprint Backlog thay đổi đáng kể |
| No Progress | Task không được cập nhật trong khoảng thời gian cấu hình |
| Meeting Reminder | Sắp đến giờ họp |

Ngưỡng cảnh báo phải cấu hình được để tránh gửi quá nhiều thông báo. Ví dụ: Sprint còn 2 ngày mà 40% Story Points chưa xong thì cảnh báo để Scrum Master kiểm tra. Đây là cảnh báo rủi ro, không phải kết luận Sprint thất bại.

## 13. Report & Export

Cấp báo cáo: Workspace, Team, Project, Sprint, Member Workload, Task, Bug, Release.

Định dạng: PDF, Excel, CSV; ảnh PNG/SVG cho biểu đồ.

Bộ lọc: thời gian, Team, Project, Sprint, thành viên, trạng thái.

## 14. Danh sách module

| STT | Module | Ưu tiên |
|---|---|---|
| 1 | Authentication & Account | Bắt buộc |
| 2 | Workspace Management | Bắt buộc |
| 3 | Team Management | Bắt buộc |
| 4 | Member & RBAC | Bắt buộc |
| 5 | Project Management | Bắt buộc |
| 6 | Task & Issue Management | Bắt buộc |
| 7 | Product Backlog | Bắt buộc |
| 8 | Sprint Management | Bắt buộc |
| 9 | Scrum Board | Bắt buộc |
| 10 | Agile Charts & Analytics | Bắt buộc |
| 11 | Document Management | Quan trọng |
| 12 | Meeting Management | Quan trọng |
| 13 | Calendar & Scheduling | Quan trọng |
| 14 | Notification & Alert | Quan trọng |
| 15 | Realtime Chat | Nâng cao |
| 16 | Collaborative Editing | Nâng cao |
| 17 | Report & Export | Quan trọng |
| 18 | Audit Log | Quan trọng |
| 19 | Administration & Settings | Quan trọng |

## 15. Quy tắc nghiệp vụ Scrum

1. **Sprint có thời gian cố định**, không quá một tháng. Khi Sprint đang chạy, Team có thể điều chỉnh kế hoạch nhưng không được làm ảnh hưởng Sprint Goal.
2. **Product Backlog khác Sprint Backlog.** Product Backlog chứa toàn bộ công việc cần phát triển; Sprint Backlog chỉ chứa công việc được chọn cho Sprint hiện tại cùng kế hoạch thực hiện.
3. **Không tự động coi Task là xong khi hết Sprint.** Chỉ Issue đáp ứng Definition of Done mới tính là Done.
4. **Story Points không phải số giờ.** Chúng ước lượng độ phức tạp và khối lượng tương đối.
5. **Burndown và Burnup phải có dữ liệu lịch sử**, bao gồm cả việc thêm, xóa hoặc thay đổi phạm vi.
6. **Meeting phải tạo ra kết quả.** Sprint Review ghi nhận phản hồi; Retrospective có Action Items và người chịu trách nhiệm.
7. **Phân quyền phải kiểm tra ở Backend**, không chỉ ẩn nút trên giao diện.
8. **Realtime phải đồng bộ dữ liệu.** Chat, Board, tài liệu và thông báo cần xử lý trạng thái kết nối, lỗi và xung đột.

## 16. Quyết định kiến trúc cho phiên bản đầu

| Vấn đề | Quyết định |
|---|---|
| Mô hình sử dụng | Multi-tenant SaaS, nhiều Workspace |
| Workspace | Có nhiều Team |
| Team | Có nhiều Project |
| Project | Một Scrum Team phụ trách chính trong MVP |
| Backlog | Thuộc phạm vi Project trong MVP |
| Thành viên | Tham gia được nhiều Team và Project |
| Phân quyền | RBAC kết hợp phạm vi Workspace/Team/Project |
| Realtime | WebSocket |
| Chỉnh sửa tài liệu | Yjs/CRDT |
| Kiến trúc | Modular Monolith |

Các lựa chọn này giúp phiên bản đầu dễ kiểm soát nhưng vẫn mở rộng được cho nhiều Scrum Team cùng phát triển một sản phẩm.

## 17. Lộ trình

| Milestone | Nội dung |
|---|---|
| M0 | Khởi tạo dự án, kiến trúc, database, môi trường |
| M1 | Đăng ký, đăng nhập, Workspace, User |
| M2 | Team, Invitation, Member, RBAC |
| M3 | Project Management, Project Overview |
| M4 | Task, Issue, Backlog, Assignment |
| M5 | Sprint Planning, Sprint Backlog, Scrum Board |
| M6 | Burndown, Burnup, Velocity, Agile Reports |
| M7 | Document Management, Version History |
| M8 | Meeting, Meeting Templates, Calendar |
| M9 | Notification, Alert, Realtime Chat |
| M10 | Collaborative Editing, Export, Audit |
| M11 | Kiểm thử, bảo mật, tối ưu và triển khai |
