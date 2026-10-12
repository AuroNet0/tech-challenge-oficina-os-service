package com.oficina.os.application.port.out;

import com.oficina.os.domain.SagaInstance;

import java.util.Optional;
import java.util.UUID;

public interface SagaRepositoryPort {

	SagaInstance save(SagaInstance saga);

	Optional<SagaInstance> findById(UUID id);

	Optional<SagaInstance> findByOrdemServicoId(UUID ordemServicoId);
}
