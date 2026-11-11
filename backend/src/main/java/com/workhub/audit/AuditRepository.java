package com.workhub.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRepository extends JpaRepository<AuditEntry, UUID>, JpaSpecificationExecutor<AuditEntry> {

	/** Only used by {@link AuditRetentionService}: nothing else in the application removes audit rows. */
	@Modifying
	@Query("delete from AuditEntry e where e.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
