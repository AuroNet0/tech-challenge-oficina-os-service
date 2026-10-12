package com.oficina.os.infrastructure.messaging;

import com.oficina.os.application.saga.SagaEventHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class SagaEventConsumer {

	private final SagaEventHandler handler;

	SagaEventConsumer(SagaEventHandler handler) {
		this.handler = handler;
	}

	@RabbitListener(queues = "${oficina.messaging.saga-events-queue}")
	void consume(String payload) {
		handler.handle(payload);
	}
}
