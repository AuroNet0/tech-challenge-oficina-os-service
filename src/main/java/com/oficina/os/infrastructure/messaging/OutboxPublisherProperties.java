package com.oficina.os.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oficina.outbox.publisher")
public record OutboxPublisherProperties(boolean enabled, int batchSize, Duration fixedDelay, Duration leaseDuration,
		int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
}
