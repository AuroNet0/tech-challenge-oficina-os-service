package com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClienteJpaRepository extends JpaRepository<ClienteJpaEntity, UUID> {

	Optional<ClienteJpaEntity> findByCpfCnpj(String cpfCnpj);

	boolean existsByCpfCnpj(String cpfCnpj);
}
