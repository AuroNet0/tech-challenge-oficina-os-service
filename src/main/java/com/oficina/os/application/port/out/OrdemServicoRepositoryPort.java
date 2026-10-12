package com.oficina.os.application.port.out;

import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrdemServicoRepositoryPort {

	OrdemServico save(OrdemServico ordemServico);

	Optional<OrdemServico> findById(UUID id);

	List<OrdemServico> findByClienteId(UUID clienteId);

	List<OrdemServico> findByVeiculoId(UUID veiculoId);

	List<OrdemServico> findByFilialId(UUID filialId);

	List<OrdemServico> findByStatus(OrdemServicoStatus status);
}
