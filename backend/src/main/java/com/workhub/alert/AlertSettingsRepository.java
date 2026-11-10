package com.workhub.alert;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertSettingsRepository extends JpaRepository<AlertSettings, UUID> {

}
