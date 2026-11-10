package com.workhub.meeting;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meeting_attendees")
public class MeetingAttendee {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "meeting_id", nullable = false, updatable = false)
	private UUID meetingId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AttendeeResponse response = AttendeeResponse.INVITED;

	protected MeetingAttendee() {
	}

	MeetingAttendee(UUID meetingId, UUID userId) {
		this.meetingId = meetingId;
		this.userId = userId;
	}

	void respond(AttendeeResponse response) {
		this.response = response;
	}

	public UUID getMeetingId() {
		return meetingId;
	}

	public UUID getUserId() {
		return userId;
	}

	public AttendeeResponse getResponse() {
		return response;
	}

}
