package com.workhub.integration;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface IntegrationTokenRepository extends JpaRepository<IntegrationToken, UUID> {

	Optional<IntegrationToken> findByTokenHash(String tokenHash);

}
