package com.workhub.user;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import com.workhub.common.ApiException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a person's account by anonymising it. The row has to stay because documents, issues, comments, chat messages
 * and audit entries point at it (they then show "Người dùng đã xóa"); everything that makes the person reachable or
 * gives them access goes: memberships, assignments, notifications, sessions, avatar, the e-mail address and the password.
 * A workspace owner has to transfer or delete the workspace first.
 */
@Service
public class AccountDeletionService {

	static final String DELETED_NAME = "Người dùng đã xóa";

	private final JdbcTemplate jdbc;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	public AccountDeletionService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void delete(UUID userId, String password) {
		User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
		}
		Integer owned = jdbc.queryForObject("select count(*) from workspace_members where user_id = ? and role = 'OWNER'",
				Integer.class, userId);
		if (owned != null && owned > 0) {
			throw ApiException.conflict("OWNS_WORKSPACES",
					"Transfer ownership of your workspaces or delete them before deleting the account");
		}
		String email = user.getEmail();

		for (String sql : new String[] { "delete from project_members where user_id = ?",
				"delete from team_members where user_id = ?", "delete from workspace_members where user_id = ?",
				"update issues set assignee_id = null where assignee_id = ?",
				"update sprints set facilitator_id = null where facilitator_id = ?",
				"update meeting_action_items set assignee_id = null where assignee_id = ?",
				"delete from meeting_attendees where user_id = ?", "delete from notifications where user_id = ?",
				"delete from notification_preferences where user_id = ?",
				"delete from conversation_members where user_id = ?", "delete from conversation_reads where user_id = ?",
				"delete from message_mentions where user_id = ?", "delete from message_reactions where user_id = ?",
				"delete from user_avatars where user_id = ?", "delete from user_tokens where user_id = ?" }) {
			jdbc.update(sql, userId);
		}
		// Invitations still waiting for this address must not be usable by whoever registers it next.
		jdbc.update("update invitations set status = 'REVOKED' where email = ? and status = 'PENDING'", email);

		byte[] noise = new byte[32];
		new SecureRandom().nextBytes(noise);
		jdbc.update("update users set email = ?, full_name = ?, avatar_url = null, email_verified = false, password_hash = ? where id = ?",
				"deleted-" + userId + "@deleted.invalid", DELETED_NAME,
				passwordEncoder.encode(Base64.getEncoder().encodeToString(noise)), userId);
	}

}
