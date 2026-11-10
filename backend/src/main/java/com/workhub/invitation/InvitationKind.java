package com.workhub.invitation;

public enum InvitationKind {

	/** Sent to one address; single use; only that account can accept or decline. */
	EMAIL,
	/** Shareable link; anyone signed in can join until it expires or is revoked. */
	LINK

}
