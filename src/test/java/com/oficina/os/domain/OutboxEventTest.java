package com.oficina.os.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

	@Test
	void createsPendingEventAndPreservesEventId() {
		UUID osId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();

		OutboxEvent event = OutboxEvent.pending(osId, OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"ok\":true}", correlationId);

		assertThat(event.getId()).isNotNull();
		assertThat(event.getAggregateId()).isEqualTo(osId);
		assertThat(event.getCorrelationId()).isEqualTo(correlationId);
		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(event.getAttempts()).isZero();
	}

	@Test
	void transitionsToPublishedOrFailedAccordingToAttempts() {
		OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"ok\":true}", null);
		Instant retryAt = Instant.now().plusSeconds(10);

		event.markFailedAttempt(retryAt, "broker unavailable", 2);

		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(event.getAttempts()).isEqualTo(1);
		assertThat(event.getNextAttemptAt()).isEqualTo(retryAt);

		event.markFailedAttempt(retryAt.plusSeconds(10), "broker unavailable", 2);

		assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(event.getNextAttemptAt()).isNull();
		assertThatThrownBy(() -> event.markPublished(Instant.now())).isInstanceOf(DomainException.class);
	}

	@Test
	void marksPublishedClearingRetryFields() {
		OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"ok\":true}", null);

		Instant publishedAt = Instant.now();
		event.markPublished(publishedAt);

		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
		assertThat(event.getLastError()).isNull();
	}
}
