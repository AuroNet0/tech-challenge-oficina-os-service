package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.validation.TextNormalizer;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
class FilialRepositoryAdapter implements FilialRepositoryPort {

	private final FilialJpaRepository repository;
	private final DomainPersistenceMapper mapper;

	FilialRepositoryAdapter(FilialJpaRepository repository, DomainPersistenceMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Override
	public Filial save(Filial filial) {
		return mapper.toDomain(repository.save(mapper.toEntity(filial)));
	}

	@Override
	public Optional<Filial> findById(UUID id) {
		return repository.findById(id).map(mapper::toDomain);
	}

	@Override
	public Optional<Filial> findByCodigo(String codigo) {
		return repository.findByCodigo(TextNormalizer.code(codigo, "codigo")).map(mapper::toDomain);
	}

	@Override
	public List<Filial> findAll() {
		return repository.findAll().stream().map(mapper::toDomain).toList();
	}

	@Override
	public boolean existsByCodigo(String codigo) {
		return repository.existsByCodigo(TextNormalizer.code(codigo, "codigo"));
	}
}
