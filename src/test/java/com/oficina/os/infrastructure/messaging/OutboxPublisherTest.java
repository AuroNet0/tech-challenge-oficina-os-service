package com.oficina.os.infrastructure.messaging;

import com.oficina.os.application.port.out.MessageBrokerPort;
import com.oficina.os.application.port.out.MessagePublishException;
import com.oficina.os.application.port.out.OutboxEventRepositoryPort;
import com.oficina.os.domain.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

	@Test
	void marksPublishedOnlyAfterBrokerPublishSucceeds() {
		OutboxEventRepositoryPort repository = mock(OutboxEventRepositoryPort.class);
		MessageBrokerPort broker = mock(MessageBrokerPort.class);
		OutboxPublisher publisher = new OutboxPublisher(repository, broker, properties());
		OutboxEvent event = event();

		publisher.publish(event);

		verify(broker).publish(event);
		verify(repository).markPublished(eq(event.getId()), any(Instant.class));
	}

	@Test
	void registersFailedAttemptWhenBrokerPublishFails() {
		OutboxEventRepositoryPort repository = mock(OutboxEventRepositoryPort.class);
		MessageBrokerPort broker = mock(MessageBrokerPort.class);
		OutboxPublisher publisher = new OutboxPublisher(repository, broker, properties());
		OutboxEvent event = event();
		when(repository.findById(event.getId())).thenReturn(Optional.of(event));
		org.mockito.Mockito.doThrow(new MessagePublishException("rabbit unavailable")).when(broker).publish(event);

		publisher.publish(event);

		verify(repository).markFailedAttempt(eq(event.getId()), any(Instant.class), eq(3), eq(Duration.ofMillis(10)),
				eq(Duration.ofSeconds(1)), eq("rabbit unavailable"));
	}

	private OutboxPublisherProperties properties() {
		return new OutboxPublisherProperties(true, 10, Duration.ofMillis(100), Duration.ofSeconds(30), 3,
				Duration.ofMillis(10), Duration.ofSeconds(1));
	}

	private OutboxEvent event() {
		return OutboxEvent.pending(UUID.randomUUID(), OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"ok\":true}", UUID.randomUUID());
	}
}
