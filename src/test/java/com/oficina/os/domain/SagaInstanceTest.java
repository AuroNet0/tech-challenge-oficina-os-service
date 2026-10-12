package com.oficina.os.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaInstanceTest {

	@Test
	void startsWaitingDiagnostic() {
		UUID osId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();

		SagaInstance saga = SagaInstance.start(osId, correlationId);

		assertThat(saga.getOrdemServicoId()).isEqualTo(osId);
		assertThat(saga.getCorrelationId()).isEqualTo(correlationId);
		assertThat(saga.getStatus()).isEqualTo(SagaStatus.WAITING_DIAGNOSTIC);
		assertThat(saga.getCurrentStep()).isEqualTo(SagaStep.DIAGNOSTIC);
	}

	@Test
	void rejectsChangesAfterTerminalState() {
		SagaInstance saga = SagaInstance.start(UUID.randomUUID(), UUID.randomUUID());

		saga.complete();

		assertThatThrownBy(() -> saga.moveTo(SagaStatus.WAITING_PAYMENT, SagaStep.PAYMENT))
				.isInstanceOf(DomainException.class);
	}

	@Test
	void rejectsIncompatibleStateRequirement() {
		SagaInstance saga = SagaInstance.start(UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> saga.require(SagaStatus.WAITING_QUOTE, SagaStep.QUOTE, "quote.created.v1"))
				.isInstanceOf(DomainException.class);
	}
}
