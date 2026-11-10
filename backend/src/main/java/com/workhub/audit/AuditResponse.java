package com.workhub.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditResponse(UUID id, Instant createdAt, UUID actorId, String actorName, String action, String targetType,
		UUID targetId, String targetLabel, String detail, String outcome, UUID projectId, String ip) {

	static AuditResponse of(AuditEntry e) {
		return new AuditResponse(e.getId(), e.getCreatedAt(), e.getActorId(), e.getActorName(), e.getAction(), e.getTargetType(),
				e.getTargetId(), e.getTargetLabel(), e.getDetail(), e.getOutcome(), e.getProjectId(), e.getIp());
	}

}
