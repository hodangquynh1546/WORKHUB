package com.workhub.meeting;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, UUID>, JpaSpecificationExecutor<Meeting> {

	@Query("select m from Meeting m where m.status = com.workhub.meeting.MeetingStatus.SCHEDULED and m.reminderMinutes is not null and m.startsAt > :now and m.startsAt <= :horizon")
	java.util.List<Meeting> findDueForReminder(@Param("now") java.time.Instant now, @Param("horizon") java.time.Instant horizon);

	/** Writes the minutes only if nobody saved since {@code baseRevision}; returns 0 rows when somebody did. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Meeting m set m.minutes = :minutes, m.minutesRevision = m.minutesRevision + 1 where m.id = :id and m.minutesRevision = :base")
	int updateMinutes(@Param("id") UUID id, @Param("minutes") String minutes, @Param("base") int base);

}
