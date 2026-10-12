package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.OrdemServicoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface OrdemServicoJpaRepository extends JpaRepository<OrdemServicoJpaEntity, UUID> {

	List<OrdemServicoJpaEntity> findByClienteId(UUID clienteId);

	List<OrdemServicoJpaEntity> findByVeiculoId(UUID veiculoId);

	List<OrdemServicoJpaEntity> findByFilialId(UUID filialId);

	List<OrdemServicoJpaEntity> findByStatus(OrdemServicoStatus status);
}
