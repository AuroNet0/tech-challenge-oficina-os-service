package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.SagaRepositoryPort;
import com.oficina.os.domain.SagaInstance;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class SagaRepositoryAdapter implements SagaRepositoryPort {

	private final SagaJpaRepository repository;

	SagaRepositoryAdapter(SagaJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public SagaInstance save(SagaInstance saga) {
		return toDomain(repository.save(toEntity(saga)));
	}

	@Override
	public Optional<SagaInstance> findById(UUID id) {
		return repository.findById(id).map(this::toDomain);
	}

	@Override
	public Optional<SagaInstance> findByOrdemServicoId(UUID ordemServicoId) {
		return repository.findByOrdemServicoId(ordemServicoId).map(this::toDomain);
	}

	private SagaJpaEntity toEntity(SagaInstance saga) {
		return new SagaJpaEntity(saga.getId(), saga.getOrdemServicoId(), saga.getStatus(), saga.getCurrentStep(),
				saga.getCreatedAt(), saga.getUpdatedAt(), saga.getCompletedAt(), saga.getCorrelationId(),
				saga.getVersion(), saga.getLastError());
	}

	private SagaInstance toDomain(SagaJpaEntity entity) {
		return SagaInstance.restore(entity.getId(), entity.getOrdemServicoId(), entity.getStatus(),
				entity.getCurrentStep(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCompletedAt(),
				entity.getCorrelationId(), entity.getVersion(), entity.getLastError());
	}
}
