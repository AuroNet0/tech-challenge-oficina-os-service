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
	String AUTHORIZED_WHERE = """
			FROM ordens_servico os WHERE
			(cast(:clienteId as uuid) IS NULL OR os.cliente_id = :clienteId)
			AND (cast(:veiculoId as uuid) IS NULL OR os.veiculo_id = :veiculoId)
			AND (cast(:filialId as uuid) IS NULL OR os.filial_id = :filialId)
			AND (cast(:status as text) IS NULL OR os.status = :status)
			AND EXISTS (SELECT 1 FROM operational_grants g WHERE g.usuario_id=:usuarioId
			AND g.filial_id=os.filial_id AND g.capability='OS_READ' AND g.ativo)
			""";

	@Query(value = "SELECT os.* " + AUTHORIZED_WHERE + " ORDER BY os.created_at DESC, os.id", countQuery = "SELECT count(*) " + AUTHORIZED_WHERE, nativeQuery = true)
	Page<OrdemServicoJpaEntity> searchAuthorized(UUID clienteId, UUID veiculoId, UUID filialId,
			String status, UUID usuarioId, Pageable pageable);

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
