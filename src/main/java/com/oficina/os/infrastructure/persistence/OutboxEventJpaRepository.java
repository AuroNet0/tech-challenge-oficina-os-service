package com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

	@Query(value = """
			UPDATE outbox_events
			   SET status = 'PROCESSING',
			       locked_until = :lockedUntil
			 WHERE id IN (
			       SELECT id
			         FROM outbox_events
			        WHERE (
			              status = 'PENDING'
			              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
			           )
			           OR (
			              status = 'PROCESSING'
			              AND locked_until <= :now
			           )
			        ORDER BY created_at
			        LIMIT :batchSize
			        FOR UPDATE SKIP LOCKED
			 )
			 RETURNING *
			""", nativeQuery = true)
	List<OutboxEventJpaEntity> acquirePending(@Param("batchSize") int batchSize, @Param("now") Instant now,
			@Param("lockedUntil") Instant lockedUntil);

	@Modifying
	@Query("""
			UPDATE OutboxEventJpaEntity event
			   SET event.status = com.oficina.os.domain.OutboxStatus.PUBLISHED,
			       event.publishedAt = :publishedAt,
			       event.lockedUntil = null,
			       event.nextAttemptAt = null,
			       event.lastError = null
			 WHERE event.id = :eventId
			   AND event.status = com.oficina.os.domain.OutboxStatus.PROCESSING
			   AND event.lockedUntil = :lockedUntil
			""")
	int markPublished(@Param("eventId") UUID eventId, @Param("publishedAt") Instant publishedAt,
			@Param("lockedUntil") Instant lockedUntil);

	@Modifying
	@Query("""
			UPDATE OutboxEventJpaEntity event
			   SET event.status = com.oficina.os.domain.OutboxStatus.PENDING,
			       event.attempts = event.attempts + 1,
			       event.nextAttemptAt = :nextAttemptAt,
			       event.lastError = :lastError,
			       event.lockedUntil = null
			 WHERE event.id = :eventId
			   AND event.status = com.oficina.os.domain.OutboxStatus.PROCESSING
			   AND event.lockedUntil = :lockedUntil
			""")
	int markRetry(@Param("eventId") UUID eventId, @Param("lockedUntil") Instant lockedUntil,
			@Param("nextAttemptAt") Instant nextAttemptAt, @Param("lastError") String lastError);

	@Modifying
	@Query("""
			UPDATE OutboxEventJpaEntity event
			   SET event.status = com.oficina.os.domain.OutboxStatus.FAILED,
			       event.attempts = event.attempts + 1,
			       event.nextAttemptAt = null,
			       event.lastError = :lastError,
			       event.lockedUntil = null
			 WHERE event.id = :eventId
			   AND event.status = com.oficina.os.domain.OutboxStatus.PROCESSING
			   AND event.lockedUntil = :lockedUntil
			""")
	int markFailed(@Param("eventId") UUID eventId, @Param("lockedUntil") Instant lockedUntil,
			@Param("lastError") String lastError);
}
