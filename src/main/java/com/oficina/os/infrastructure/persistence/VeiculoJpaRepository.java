package com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VeiculoJpaRepository extends JpaRepository<VeiculoJpaEntity, UUID> {

	Optional<VeiculoJpaEntity> findByPlaca(String placa);

	List<VeiculoJpaEntity> findByClienteId(UUID clienteId);

	boolean existsByPlaca(String placa);
}
