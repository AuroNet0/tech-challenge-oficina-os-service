package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.validation.CpfCnpj;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
class ClienteRepositoryAdapter implements ClienteRepositoryPort {

	private final ClienteJpaRepository repository;
	private final DomainPersistenceMapper mapper;

	ClienteRepositoryAdapter(ClienteJpaRepository repository, DomainPersistenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Override
	public Cliente save(Cliente cliente) {
		return mapper.toDomain(repository.save(mapper.toEntity(cliente)));
	}

	@Override
	public Optional<Cliente> findById(UUID id) {
		return repository.findById(id).map(mapper::toDomain);
	}

	@Override
	public Optional<Cliente> findByCpfCnpj(String cpfCnpj) {
		return repository.findByCpfCnpj(CpfCnpj.normalize(cpfCnpj)).map(mapper::toDomain);
	}

	@Override
	public List<Cliente> findAll() {
		return repository.findAll().stream().map(mapper::toDomain).toList();
	}

	@Override
	public boolean existsByCpfCnpj(String cpfCnpj) {
		return repository.existsByCpfCnpj(CpfCnpj.normalize(cpfCnpj));
	}
}
