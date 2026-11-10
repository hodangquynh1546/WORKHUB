package com.workhub.document;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentCollabRepository extends JpaRepository<DocumentCollab, UUID> {

	/** A snapshot or reset in progress waits for readers to finish, and readers wait for it: state is read consistently. */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select c from DocumentCollab c where c.documentId = :id")
	Optional<DocumentCollab> findForRead(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from DocumentCollab c where c.documentId = :id")
	Optional<DocumentCollab> findForWrite(@Param("id") UUID id);

}
