package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.domain.Veiculo;
import com.oficina.os.domain.validation.Placa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class VeiculoRepositoryAdapter implements VeiculoRepositoryPort {

	private final VeiculoJpaRepository repository;
	private final DomainPersistenceMapper mapper;

	VeiculoRepositoryAdapter(VeiculoJpaRepository repository, DomainPersistenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Override
	public Veiculo save(Veiculo veiculo) {
		return mapper.toDomain(repository.save(mapper.toEntity(veiculo)));
	}

	@Override
	public Optional<Veiculo> findById(UUID id) {
		return repository.findById(id).map(mapper::toDomain);
	}

	@Override
	public Optional<Veiculo> findByPlaca(String placa) {
		return repository.findByPlaca(Placa.normalize(placa)).map(mapper::toDomain);
	}

	@Override
	public List<Veiculo> findByClienteId(UUID clienteId) {
		return repository.findByClienteId(clienteId).stream().map(mapper::toDomain).toList();
	}

	@Override
	public boolean existsByPlaca(String placa) {
		return repository.existsByPlaca(Placa.normalize(placa));
	}
}
