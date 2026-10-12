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

	void markFailedAttempt(UUID eventId, Instant now, int maxAttempts, Duration initialBackoff, Duration maxBackoff,
			String error);

	Optional<OutboxEvent> findById(UUID eventId);
}
