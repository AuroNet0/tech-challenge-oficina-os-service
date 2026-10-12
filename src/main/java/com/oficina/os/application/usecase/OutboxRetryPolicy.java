package com.oficina.os.application.usecase;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OutboxRetryPolicy {

	public Duration nextDelay(int failedAttempts, Duration initialBackoff, Duration maxBackoff) {
		if (failedAttempts <= 0) {
			return initialBackoff;
		}
		long multiplier = 1L << Math.min(failedAttempts - 1, 30);
		long seconds = Math.multiplyExact(initialBackoff.toSeconds(), multiplier);
		return Duration.ofSeconds(Math.min(seconds, maxBackoff.toSeconds()));
	}
}
