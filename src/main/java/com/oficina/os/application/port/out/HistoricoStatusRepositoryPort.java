package com.oficina.os.application.port.out;

import com.oficina.os.domain.HistoricoStatus;

import java.util.List;
import java.util.UUID;

public interface HistoricoStatusRepositoryPort {

	HistoricoStatus save(HistoricoStatus historicoStatus);

	List<HistoricoStatus> findByOrdemServicoId(UUID ordemServicoId);
}
