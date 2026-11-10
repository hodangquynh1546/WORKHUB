package com.workhub.invitation;

/** Stored status. EXPIRED is not stored: it is derived from {@code expires_at} (see {@link Invitation#statusAt}). */
public enum InvitationStatus {

	PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED

}
