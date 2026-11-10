package com.workhub.meeting;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingActionItemRepository extends JpaRepository<MeetingActionItem, UUID> {

	List<MeetingActionItem> findAllByMeetingIdOrderByCreatedAtAscIdAsc(UUID meetingId);

	/** Rows: meeting id, number of items still open. */
	@Query("select a.meetingId, count(a) from MeetingActionItem a where a.meetingId in :ids and a.status = com.workhub.meeting.ActionItemStatus.OPEN group by a.meetingId")
	List<Object[]> openCountsFor(@Param("ids") Collection<UUID> ids);

}
