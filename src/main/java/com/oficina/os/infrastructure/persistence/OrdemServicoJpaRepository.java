package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.OrdemServicoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface OrdemServicoJpaRepository extends JpaRepository<OrdemServicoJpaEntity, UUID> {

	List<OrdemServicoJpaEntity> findByClienteId(UUID clienteId);

	List<OrdemServicoJpaEntity> findByVeiculoId(UUID veiculoId);

	List<OrdemServicoJpaEntity> findByFilialId(UUID filialId);

	List<OrdemServicoJpaEntity> findByStatus(OrdemServicoStatus status);

	@Query("""
			SELECT os
			FROM OrdemServicoJpaEntity os
			WHERE (:clienteId IS NULL OR os.clienteId = :clienteId)
			  AND (:veiculoId IS NULL OR os.veiculoId = :veiculoId)
			  AND (:filialId IS NULL OR os.filialId = :filialId)
			  AND (:status IS NULL OR os.status = :status)
			""")
	Page<OrdemServicoJpaEntity> search(
			@Param("clienteId") UUID clienteId,
			@Param("veiculoId") UUID veiculoId,
			@Param("filialId") UUID filialId,
			@Param("status") OrdemServicoStatus status,
			Pageable pageable);
}
