package com.workhub.project;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAvatarRepository extends JpaRepository<ProjectAvatar, UUID> {

}
