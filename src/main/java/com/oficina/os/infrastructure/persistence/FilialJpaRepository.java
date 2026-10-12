package com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface FilialJpaRepository extends JpaRepository<FilialJpaEntity, UUID> {

	Optional<FilialJpaEntity> findByCodigo(String codigo);

	boolean existsByCodigo(String codigo);
}
