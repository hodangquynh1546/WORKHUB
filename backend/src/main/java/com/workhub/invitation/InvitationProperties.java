package com.workhub.invitation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workhub.invitations")
public record InvitationProperties(Duration ttl) {
}
