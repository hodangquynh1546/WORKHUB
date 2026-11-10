package com.workhub.meeting;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, UUID> {

	List<MeetingAttendee> findAllByMeetingId(UUID meetingId);

	List<MeetingAttendee> findAllByMeetingIdIn(Collection<UUID> meetingIds);

	Optional<MeetingAttendee> findByMeetingIdAndUserId(UUID meetingId, UUID userId);

	/** Rows: meeting id, number of attendees. */
	@Query("select a.meetingId, count(a) from MeetingAttendee a where a.meetingId in :ids group by a.meetingId")
	List<Object[]> countsFor(@Param("ids") Collection<UUID> ids);

}
