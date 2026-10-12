package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OrdemServicoRepositoryAdapter implements OrdemServicoRepositoryPort {

	private final OrdemServicoJpaRepository repository;
	private final DomainPersistenceMapper mapper;

	OrdemServicoRepositoryAdapter(OrdemServicoJpaRepository repository, DomainPersistenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Override
	public OrdemServico save(OrdemServico ordemServico) {
		return mapper.toDomain(repository.save(mapper.toEntity(ordemServico)));
	}

	@Override
	public Optional<OrdemServico> findById(UUID id) {
		return repository.findById(id).map(mapper::toDomain);
	}

	@Override
	public List<OrdemServico> findByClienteId(UUID clienteId) {
		return repository.findByClienteId(clienteId).stream().map(mapper::toDomain).toList();
	}

	@Override
	public List<OrdemServico> findByVeiculoId(UUID veiculoId) {
		return repository.findByVeiculoId(veiculoId).stream().map(mapper::toDomain).toList();
	}

	@Override
	public List<OrdemServico> findByFilialId(UUID filialId) {
		return repository.findByFilialId(filialId).stream().map(mapper::toDomain).toList();
	}

	@Override
	public List<OrdemServico> findByStatus(OrdemServicoStatus status) {
		return repository.findByStatus(status).stream().map(mapper::toDomain).toList();
	}
}
