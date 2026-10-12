package com.oficina.os.infrastructure.messaging;

import com.oficina.os.application.port.out.MessagePublishException;
import com.oficina.os.domain.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RabbitMessageBrokerAdapterIntegrationTest {

	@Container
	static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

	@Test
	void publishesPersistentMessageAfterPositiveBrokerConfirm() {
		String exchange = "test.commands." + UUID.randomUUID();
		String queueName = "test.diagnostic." + UUID.randomUUID();
		CachingConnectionFactory connectionFactory = connectionFactory();
		RabbitAdmin admin = new RabbitAdmin(connectionFactory);
		TopicExchange topicExchange = new TopicExchange(exchange, true, false);
		Queue queue = new Queue(queueName, true);
		admin.declareExchange(topicExchange);
		admin.declareQueue(queue);
		admin.declareBinding(BindingBuilder.bind(queue).to(topicExchange).with(OutboxEvent.DIAGNOSTIC_START_V1));
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMandatory(true);
		RabbitMessageBrokerAdapter adapter = new RabbitMessageBrokerAdapter(template,
				new RabbitMessagingProperties(exchange, OutboxEvent.DIAGNOSTIC_START_V1, queueName,
						Duration.ofSeconds(5)));
		OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"eventId\":\"ok\"}", UUID.randomUUID());

		adapter.publish(event);
		Message message = template.receive(queueName, 5000);

		assertThat(message).isNotNull();
		assertThat(new String(message.getBody())).isEqualTo(event.getPayload());
		assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
		assertThat(message.getMessageProperties().getReceivedDeliveryMode().name()).isEqualTo("PERSISTENT");
		connectionFactory.destroy();
	}

	@Test
	void rejectsReturnedUnroutableMessageEvenWhenBrokerAcksPublish() {
		String exchange = "test.commands.unroutable." + UUID.randomUUID();
		CachingConnectionFactory connectionFactory = connectionFactory();
		RabbitAdmin admin = new RabbitAdmin(connectionFactory);
		admin.declareExchange(new TopicExchange(exchange, true, false));
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMandatory(true);
		RabbitMessageBrokerAdapter adapter = new RabbitMessageBrokerAdapter(template,
				new RabbitMessagingProperties(exchange, OutboxEvent.DIAGNOSTIC_START_V1, "unused",
						Duration.ofSeconds(5)));
		OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"eventId\":\"unroutable\"}", UUID.randomUUID());

		assertThatThrownBy(() -> adapter.publish(event)).isInstanceOf(MessagePublishException.class)
				.hasMessageContaining("unroutable");
		connectionFactory.destroy();
	}

	@Test
	void failsWhenRabbitMqIsUnavailable() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost", 1);
		connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
		connectionFactory.setPublisherReturns(true);
		RabbitTemplate template = new RabbitTemplate(connectionFactory);
		template.setMandatory(true);
		RabbitMessageBrokerAdapter adapter = new RabbitMessageBrokerAdapter(template,
				new RabbitMessagingProperties("missing", OutboxEvent.DIAGNOSTIC_START_V1, "missing",
						Duration.ofMillis(100)));
		OutboxEvent event = OutboxEvent.pending(UUID.randomUUID(), OutboxEvent.ORDEM_SERVICO,
				OutboxEvent.DIAGNOSTIC_START_V1, "{\"eventId\":\"down\"}", UUID.randomUUID());

		assertThatThrownBy(() -> adapter.publish(event)).isInstanceOf(MessagePublishException.class);
		connectionFactory.destroy();
	}

	private CachingConnectionFactory connectionFactory() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
		connectionFactory.setUsername(RABBIT.getAdminUsername());
		connectionFactory.setPassword(RABBIT.getAdminPassword());
		connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
		connectionFactory.setPublisherReturns(true);
		return connectionFactory;
	}
}
