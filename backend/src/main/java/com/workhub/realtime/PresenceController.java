package com.workhub.realtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Which of the given people are online (have an open WebSocket). Ids are random UUIDs; at most 100 per call. */
@RestController
public class PresenceController {

	private final RealtimeHub hub;

	public PresenceController(RealtimeHub hub) {
		this.hub = hub;
	}

	@GetMapping("/api/presence")
	public Map<String, Set<UUID>> presence(@RequestParam List<UUID> userIds) {
		return Map.of("online", hub.online(userIds.stream().limit(100).toList()));
	}

}
