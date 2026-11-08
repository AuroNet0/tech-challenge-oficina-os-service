package com.oficina.os.application.port.out;

import com.oficina.os.domain.OutboxEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepositoryPort {

	OutboxEvent save(OutboxEvent event);

	List<OutboxEvent> acquirePending(int batchSize, Instant now, Duration leaseDuration);

	void markPublished(UUID eventId, Instant publishedAt);

	default boolean markPublished(UUID eventId, Instant publishedAt, Instant lockedUntil) {
		markPublished(eventId, publishedAt);
		return true;
	}

	void markFailedAttempt(UUID eventId, Instant now, int maxAttempts, Duration initialBackoff, Duration maxBackoff,
			String error);

	default boolean markFailedAttempt(UUID eventId, Instant lockedUntil, Instant now, int maxAttempts,
			Duration initialBackoff, Duration maxBackoff, String error) {
		markFailedAttempt(eventId, now, maxAttempts, initialBackoff, maxBackoff, error);
		return true;
	}

	Optional<OutboxEvent> findById(UUID eventId);
}
