package com.oficina.os.domain;

public enum OutboxStatus {
	PENDING,
	PROCESSING,
	PUBLISHED,
	FAILED
}
