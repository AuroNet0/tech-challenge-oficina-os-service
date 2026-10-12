package com.oficina.os.infrastructure.messaging;

import com.oficina.os.application.port.out.MessageBrokerPort;
import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "oficina.outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboxPublisher {

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

	private final OutboxEventRepositoryPort outboxEvents;
	private final MessageBrokerPort broker;
	private final OutboxPublisherProperties properties;

	OutboxPublisher(OutboxEventRepositoryPort outboxEvents, MessageBrokerPort broker,
			OutboxPublisherProperties properties) {
		this.outboxEvents = outboxEvents;
		this.broker = broker;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${oficina.outbox.publisher.fixed-delay:5s}")
	void publishPendingEvents() {
		if (!properties.enabled()) {
			return;
		}
		Instant now = Instant.now();
		List<OutboxEvent> events = outboxEvents.acquirePending(properties.batchSize(), now, properties.leaseDuration());
		if (events.isEmpty()) {
			return;
		}
		LOGGER.info("outbox batch acquired count={}", events.size());
		for (OutboxEvent event : events) {
			publish(event);
		}
	}

	void publish(OutboxEvent event) {
		LOGGER.info("outbox publication attempt eventId={} osId={} correlationId={} attempt={}", event.getId(),
				event.getAggregateId(), event.getCorrelationId(), event.getAttempts() + 1);
		try {
			broker.publish(event);
			boolean updated = outboxEvents.markPublished(event.getId(), Instant.now(), event.getLockedUntil());
			if (!updated) {
				LOGGER.warn("outbox publication confirm ignored after lease mismatch eventId={} osId={} correlationId={}",
						event.getId(), event.getAggregateId(), event.getCorrelationId());
				return;
			}
			LOGGER.info("outbox publication confirmed eventId={} osId={} correlationId={}", event.getId(),
					event.getAggregateId(), event.getCorrelationId());
		}
		catch (RuntimeException ex) {
			boolean updated = outboxEvents.markFailedAttempt(event.getId(), event.getLockedUntil(), Instant.now(), properties.maxAttempts(),
					properties.initialBackoff(), properties.maxBackoff(), ex.getMessage());
			LOGGER.warn("outbox publication failed eventId={} osId={} correlationId={} updated={} error={}",
					event.getId(), event.getAggregateId(), event.getCorrelationId(), updated, ex.getMessage());
		}
	}
}
