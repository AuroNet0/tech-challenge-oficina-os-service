package com.oficina.os.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oficina.messaging")
public record RabbitMessagingProperties(String exchange, String eventsExchange, String diagnosticStartRoutingKey,
		String diagnosticStartQueue, String sagaEventsQueue, String sagaEventsDlq, Duration confirmTimeout) {
}
