package com.workhub.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

	Optional<UserToken> findByTypeAndTokenHash(TokenType type, String tokenHash);

	/**
	 * Bulk update: pending changes are flushed first and the persistence context is cleared afterwards,
	 * so entities loaded earlier in the transaction are detached. Call it after modifying them.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update UserToken t set t.revokedAt = :now where t.userId = :userId and t.type = :type and t.consumedAt is null and t.revokedAt is null")
	int revokeAll(@Param("userId") UUID userId, @Param("type") TokenType type, @Param("now") Instant now);

}
