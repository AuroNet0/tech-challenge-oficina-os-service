package com.oficina.os.application.port.out;

import com.oficina.os.domain.OutboxEvent;

public interface MessageBrokerPort {

	void publish(OutboxEvent event);
}
