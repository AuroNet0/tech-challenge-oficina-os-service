package com.oficina.os.application.usecase;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryPolicyTest {

	private final OutboxRetryPolicy policy = new OutboxRetryPolicy();

	@Test
	void calculatesLimitedExponentialBackoff() {
		assertThat(policy.nextDelay(1, Duration.ofSeconds(10), Duration.ofMinutes(5))).isEqualTo(Duration.ofSeconds(10));
		assertThat(policy.nextDelay(2, Duration.ofSeconds(10), Duration.ofMinutes(5))).isEqualTo(Duration.ofSeconds(20));
		assertThat(policy.nextDelay(10, Duration.ofSeconds(10), Duration.ofMinutes(5))).isEqualTo(Duration.ofMinutes(5));
	}
}
