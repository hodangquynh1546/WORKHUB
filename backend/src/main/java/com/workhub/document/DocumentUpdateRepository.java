package com.workhub.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdate, Long> {

	List<DocumentUpdate> findAllByDocumentIdAndEpochAndSeqGreaterThanOrderBySeqAsc(UUID documentId, int epoch, long seq);

	boolean existsByDocumentIdAndEpoch(UUID documentId, int epoch);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from DocumentUpdate u where u.documentId = :id and u.seq <= :seq")
	int deleteUpTo(@Param("id") UUID documentId, @Param("seq") long seq);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from DocumentUpdate u where u.documentId = :id")
	int deleteAll(@Param("id") UUID documentId);

	@Query("select coalesce(max(u.seq), 0) from DocumentUpdate u where u.documentId = :id and u.epoch = :epoch")
	long lastSeq(@Param("id") UUID documentId, @Param("epoch") int epoch);

}
