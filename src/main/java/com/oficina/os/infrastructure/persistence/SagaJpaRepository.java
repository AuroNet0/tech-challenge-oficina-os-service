package com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SagaJpaRepository extends JpaRepository<SagaJpaEntity, UUID> {

	Optional<SagaJpaEntity> findByOrdemServicoId(UUID ordemServicoId);
}
