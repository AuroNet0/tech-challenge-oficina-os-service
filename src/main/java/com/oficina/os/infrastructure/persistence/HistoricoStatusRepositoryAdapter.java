package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.domain.HistoricoStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class HistoricoStatusRepositoryAdapter implements HistoricoStatusRepositoryPort {

	private final HistoricoStatusJpaRepository repository;
	private final DomainPersistenceMapper mapper;

	HistoricoStatusRepositoryAdapter(HistoricoStatusJpaRepository repository, DomainPersistenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Override
	public HistoricoStatus save(HistoricoStatus historicoStatus) {
		return mapper.toDomain(repository.save(mapper.toEntity(historicoStatus)));
	}

	@Override
	public List<HistoricoStatus> findByOrdemServicoId(UUID ordemServicoId) {
		return repository.findByOrdemServicoIdOrderByCreatedAtAsc(ordemServicoId)
				.stream()
				.map(mapper::toDomain)
				.toList();
	}
}
