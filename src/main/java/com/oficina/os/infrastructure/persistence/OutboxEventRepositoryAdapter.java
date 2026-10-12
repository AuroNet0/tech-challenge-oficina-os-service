package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.application.usecase.OutboxRetryPolicy;
import com.oficina.os.domain.OutboxEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OutboxEventRepositoryAdapter implements OutboxEventRepositoryPort {

	private final OutboxEventJpaRepository repository;
	private final OutboxRetryPolicy retryPolicy;

	OutboxEventRepositoryAdapter(OutboxEventJpaRepository repository, OutboxRetryPolicy retryPolicy) {
		this.repository = repository;
		this.retryPolicy = retryPolicy;
	}

	@Override
	public OutboxEvent save(OutboxEvent event) {
		return toDomain(repository.save(toEntity(event)));
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<OutboxEvent> acquirePending(int batchSize, Instant now, Duration leaseDuration) {
		return repository.acquirePending(batchSize, now, now.plus(leaseDuration)).stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPublished(UUID eventId, Instant publishedAt) {
		repository.markPublished(eventId, publishedAt);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailedAttempt(UUID eventId, Instant now, int maxAttempts, Duration initialBackoff,
			Duration maxBackoff, String error) {
		OutboxEvent event = repository.findById(eventId).map(this::toDomain)
				.orElseThrow(() -> new IllegalStateException("outbox event not found: " + eventId));
		String normalizedError = error == null || error.isBlank() ? "unknown publication failure" : error;
		if (event.getAttempts() + 1 >= maxAttempts) {
			repository.markFailed(eventId, trim(normalizedError));
			return;
		}
		Duration nextDelay = retryPolicy.nextDelay(event.getAttempts() + 1, initialBackoff, maxBackoff);
		repository.markRetry(eventId, now.plus(nextDelay), trim(normalizedError));
	}

	@Override
	public Optional<OutboxEvent> findById(UUID eventId) {
		return repository.findById(eventId).map(this::toDomain);
	}

	private OutboxEventJpaEntity toEntity(OutboxEvent event) {
		return new OutboxEventJpaEntity(event.getId(), event.getAggregateId(), event.getAggregateType(),
				event.getEventType(), event.getPayload(), event.getStatus(), event.getCorrelationId(),
				event.getCreatedAt(), event.getPublishedAt(), event.getAttempts(), event.getNextAttemptAt(),
				event.getLastError(), event.getLockedUntil());
	}

	private OutboxEvent toDomain(OutboxEventJpaEntity entity) {
		return OutboxEvent.restore(entity.getId(), entity.getAggregateId(), entity.getAggregateType(),
				entity.getEventType(), entity.getPayload(), entity.getStatus(), entity.getCorrelationId(),
				entity.getCreatedAt(), entity.getPublishedAt(), entity.getAttempts(), entity.getNextAttemptAt(),
				entity.getLastError(), entity.getLockedUntil());
	}

	private String trim(String value) {
		return value.length() <= 4000 ? value : value.substring(0, 4000);
	}
}
