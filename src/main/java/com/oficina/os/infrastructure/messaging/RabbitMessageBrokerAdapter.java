package com.oficina.os.infrastructure.messaging;

import com.oficina.os.application.port.out.MessageBrokerPort;
import com.oficina.os.application.port.out.MessagePublishException;
import com.oficina.os.domain.OutboxEvent;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
class RabbitMessageBrokerAdapter implements MessageBrokerPort {

	private final RabbitTemplate rabbitTemplate;
	private final RabbitMessagingProperties properties;

	RabbitMessageBrokerAdapter(RabbitTemplate rabbitTemplate, RabbitMessagingProperties properties) {
		this.rabbitTemplate = rabbitTemplate;
		this.properties = properties;
	}

	@Override
	public void publish(OutboxEvent event) {
		CorrelationData correlationData = new CorrelationData(event.getId().toString());
		try {
			rabbitTemplate.convertAndSend(properties.exchange(), event.getEventType(), event.getPayload(), message -> {
				MessageProperties messageProperties = message.getMessageProperties();
				messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
				messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
				messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
				messageProperties.setMessageId(event.getId().toString());
				if (event.getCorrelationId() != null) {
					messageProperties.setCorrelationId(event.getCorrelationId().toString());
				}
				messageProperties.setHeader("eventType", event.getEventType());
				messageProperties.setHeader("aggregateId", event.getAggregateId().toString());
				messageProperties.setHeader("aggregateType", event.getAggregateType());
				return message;
			}, correlationData);
		}
		catch (AmqpException ex) {
			throw new MessagePublishException("could not send eventId=" + event.getId(), ex);
		}

		CorrelationData.Confirm confirm = waitForConfirm(event.getId(), correlationData);
		if (!confirm.isAck()) {
			throw new MessagePublishException("broker rejected eventId=%s reason=%s"
					.formatted(event.getId(), confirm.getReason()));
		}
		if (correlationData.getReturned() != null) {
			throw new MessagePublishException("message was returned as unroutable eventId=%s replyText=%s"
					.formatted(event.getId(), correlationData.getReturned().getReplyText()));
		}
	}

	private CorrelationData.Confirm waitForConfirm(UUID eventId, CorrelationData correlationData) {
		try {
			return correlationData.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new MessagePublishException("interrupted while waiting broker confirm eventId=" + eventId, ex);
		}
		catch (ExecutionException | TimeoutException ex) {
			throw new MessagePublishException("could not confirm publication eventId=" + eventId, ex);
		}
	}
}
