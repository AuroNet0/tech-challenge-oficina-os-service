package com.oficina.os.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ RabbitMessagingProperties.class, OutboxPublisherProperties.class })
class RabbitMessagingConfig {

	@Bean
	Declarables diagnosticStartRabbitDeclarations(RabbitMessagingProperties properties) {
		TopicExchange exchange = new TopicExchange(properties.exchange(), true, false);
		Queue queue = new Queue(properties.diagnosticStartQueue(), true);
		Binding binding = BindingBuilder.bind(queue).to(exchange).with(properties.diagnosticStartRoutingKey());
		TopicExchange eventsExchange = new TopicExchange(properties.eventsExchange(), true, false);
		Queue sagaEventsQueue = QueueBuilder.durable(properties.sagaEventsQueue())
				.deadLetterExchange(properties.eventsExchange() + ".dlx")
				.deadLetterRoutingKey("saga.events.dead")
				.build();
		TopicExchange dlx = new TopicExchange(properties.eventsExchange() + ".dlx", true, false);
		Queue dlq = QueueBuilder.durable(properties.sagaEventsDlq()).build();
		Binding eventsBinding = BindingBuilder.bind(sagaEventsQueue).to(eventsExchange).with("#.v1");
		Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with("saga.events.dead");
		return new Declarables(exchange, queue, binding, eventsExchange, sagaEventsQueue, eventsBinding, dlx, dlq,
				dlqBinding);
	}
}
