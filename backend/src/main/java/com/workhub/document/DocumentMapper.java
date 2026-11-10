package com.workhub.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workhub.access.ProjectAccess;
import com.workhub.access.ProjectPermission;
import com.workhub.document.DocumentAttachment.AttachmentRow;
import com.workhub.document.DocumentResponses.AttachmentResponse;
import com.workhub.document.DocumentResponses.DocumentDetail;
import com.workhub.document.DocumentResponses.DocumentSummary;
import com.workhub.document.DocumentResponses.LinkResponse;
import com.workhub.issue.Issue;
import com.workhub.issue.IssueMapper;
import com.workhub.issue.IssueRepository;
import com.workhub.issue.IssueResponses.UserRef;
import com.workhub.meeting.MeetingRepository;
import com.workhub.sprint.Sprint;
import com.workhub.sprint.SprintRepository;
import com.workhub.user.User;
import com.workhub.user.UserRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Entities to responses with a fixed number of queries per call (authors, attachment counts, link targets). */
@Component
@Transactional(readOnly = true)
class DocumentMapper {

	private static final int EXCERPT = 160;

	private final UserRepository users;

	private final DocumentAttachmentRepository attachments;

	private final DocumentLinkRepository links;

	private final IssueRepository issues;

	private final SprintRepository sprints;

	private final MeetingRepository meetings;

	DocumentMapper(UserRepository users, DocumentAttachmentRepository attachments, DocumentLinkRepository links,
			IssueRepository issues, SprintRepository sprints,
			MeetingRepository meetings) {
		this.users = users;
		this.attachments = attachments;
		this.links = links;
		this.issues = issues;
		this.sprints = sprints;
		this.meetings = meetings;
	}

	List<DocumentSummary> summaries(List<Document> documents) {
		if (documents.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = documents.stream().map(Document::getId).toList();
		Map<UUID, Integer> counts = new HashMap<>();
		attachments.countsFor(ids).forEach((row) -> counts.put((UUID) row[0], ((Number) row[1]).intValue()));
		Map<UUID, UserRef> people = userRefs(documents.stream().map(Document::getUpdatedBy).toList());
		return documents.stream()
			.map((d) -> new DocumentSummary(d.getId(), d.getTitle(), d.getType(), d.getFolderId(), d.getStatus(),
					d.getVersionNumber(), excerpt(d.getContent()), people.get(d.getUpdatedBy()), d.getUpdatedAt(),
					counts.getOrDefault(d.getId(), 0)))
			.toList();
	}

	DocumentDetail detail(Document d, ProjectAccess access) {
		List<AttachmentRow> rows = attachments.rows(d.getId());
		Set<UUID> authors = new HashSet<>(List.of(d.getCreatedBy(), d.getUpdatedBy()));
		rows.forEach((r) -> authors.add(r.uploadedBy()));
		Map<UUID, UserRef> people = userRefs(authors);
		Set<ProjectPermission> permissions = access.permissions();
		boolean canEdit = permissions.contains(ProjectPermission.EDIT_DOCUMENTS) && !access.project().isArchived()
				&& d.getStatus() == DocumentStatus.ACTIVE;
		boolean canDelete = permissions.contains(ProjectPermission.EDIT_DOCUMENTS) && !access.project().isArchived()
				&& (d.getCreatedBy().equals(access.userId()) || permissions.contains(ProjectPermission.UPDATE_PROJECT));
		return new DocumentDetail(d.getId(), d.getProjectId(), d.getTitle(), d.getType(), d.getFolderId(), d.getStatus(),
				d.getVersionNumber(), d.getContent(), people.get(d.getCreatedBy()), people.get(d.getUpdatedBy()),
				d.getCreatedAt(), d.getUpdatedAt(),
				rows.stream()
					.map((r) -> new AttachmentResponse(r.id(), r.fileName(), r.contentType(), r.sizeBytes(),
							people.get(r.uploadedBy()), r.createdAt()))
					.toList(),
				linkResponses(d.getId(), access.project().getProjectKey()), canEdit, canDelete);
	}

	Map<UUID, UserRef> userRefs(Collection<UUID> ids) {
		Map<UUID, UserRef> out = new HashMap<>();
		for (User user : users.findAllById(new HashSet<>(ids))) {
			out.put(user.getId(), IssueMapper.userRef(user));
		}
		return out;
	}

	private List<LinkResponse> linkResponses(UUID documentId, String projectKey) {
		List<DocumentLink> all = links.findAllByDocumentId(documentId);
		if (all.isEmpty()) {
			return List.of();
		}
		Map<UUID, Issue> issueById = new HashMap<>();
		issues.findAllById(all.stream().filter((l) -> l.getTargetType() == LinkTargetType.ISSUE).map(DocumentLink::getTargetId).toList())
			.forEach((i) -> issueById.put(i.getId(), i));
		Map<UUID, Sprint> sprintById = new HashMap<>();
		sprints.findAllById(all.stream().filter((l) -> l.getTargetType() == LinkTargetType.SPRINT).map(DocumentLink::getTargetId).toList())
			.forEach((s) -> sprintById.put(s.getId(), s));
		Map<UUID, com.workhub.meeting.Meeting> meetingById = new HashMap<>();
		meetings.findAllById(all.stream().filter((l) -> l.getTargetType() == LinkTargetType.MEETING).map(DocumentLink::getTargetId).toList())
			.forEach((m) -> meetingById.put(m.getId(), m));
		List<LinkResponse> out = new ArrayList<>();
		for (DocumentLink link : all) {
			// Targets that were deleted since are simply not shown.
			if (link.getTargetType() == LinkTargetType.ISSUE) {
				Issue issue = issueById.get(link.getTargetId());
				if (issue != null) {
					out.add(new LinkResponse(LinkTargetType.ISSUE, issue.getId(),
							projectKey + "-" + issue.getNumber() + " " + issue.getTitle(), issue.getStatus().name()));
				}
			}
			else if (link.getTargetType() == LinkTargetType.MEETING) {
				var meeting = meetingById.get(link.getTargetId());
				if (meeting != null) {
					out.add(new LinkResponse(LinkTargetType.MEETING, meeting.getId(), meeting.getTitle(),
							meeting.getStatus().name()));
				}
			}
			else {
				Sprint sprint = sprintById.get(link.getTargetId());
				if (sprint != null) {
					out.add(new LinkResponse(LinkTargetType.SPRINT, sprint.getId(), sprint.getName(),
							sprint.getStatus().name()));
				}
			}
		}
		return out;
	}

	static String excerpt(String content) {
		String plain = content.replaceAll("[#>*`_~|\\[\\]]", " ").replaceAll("\\s+", " ").strip();
		return (plain.length() <= EXCERPT) ? plain : plain.substring(0, EXCERPT).stripTrailing() + "…";
	}

}
