package com.oficina.os.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface HistoricoStatusJpaRepository extends JpaRepository<HistoricoStatusJpaEntity, UUID> {

	List<HistoricoStatusJpaEntity> findByOrdemServicoIdOrderByCreatedAtAsc(UUID ordemServicoId);
}
