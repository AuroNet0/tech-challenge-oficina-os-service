package com.oficina.os.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InboxMessageJpaRepository extends JpaRepository<InboxMessageJpaEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT message FROM InboxMessageJpaEntity message WHERE message.eventId = :eventId")
	Optional<InboxMessageJpaEntity> findByEventIdForUpdate(@Param("eventId") UUID eventId);
}
