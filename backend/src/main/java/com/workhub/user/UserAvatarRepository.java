package com.workhub.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {

}
