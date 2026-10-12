package com.oficina.os.application.saga;

import com.oficina.os.domain.DomainException;
import com.oficina.os.domain.SagaInstance;
import com.oficina.os.domain.SagaStatus;
import com.oficina.os.domain.SagaStep;
import org.springframework.stereotype.Component;

@Component
public class SagaStateMachine {

	public void requireDiagnostic(SagaInstance saga, String eventType) {
		saga.require(SagaStatus.WAITING_DIAGNOSTIC, SagaStep.DIAGNOSTIC, eventType);
	}

	public void requireQuote(SagaInstance saga, String eventType) {
		saga.require(SagaStatus.WAITING_QUOTE, SagaStep.QUOTE, eventType);
	}

	public void requireApproval(SagaInstance saga, String eventType) {
		saga.require(SagaStatus.WAITING_APPROVAL, SagaStep.APPROVAL, eventType);
	}

	public void requireRepair(SagaInstance saga, String eventType) {
		saga.require(SagaStatus.WAITING_REPAIR, SagaStep.REPAIR, eventType);
	}

	public void requirePayment(SagaInstance saga, String eventType) {
		saga.require(SagaStatus.WAITING_PAYMENT, SagaStep.PAYMENT, eventType);
	}

	public void validateEnvelope(SagaInstance saga, SagaEnvelope envelope) {
		if (!saga.getId().equals(envelope.sagaId())) {
			throw new DomainException("event belongs to another saga");
		}
		if (!saga.getOrdemServicoId().equals(envelope.osId())) {
			throw new DomainException("event belongs to another ordemServico");
		}
	}
}
