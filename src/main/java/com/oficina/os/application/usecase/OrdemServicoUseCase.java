package com.oficina.os.application.usecase;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import com.oficina.os.application.exception.ResourceNotFoundException;
import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoSearchCriteria;
import com.oficina.os.application.port.out.PageResult;
import com.oficina.os.application.port.out.SagaRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.application.saga.OrdemServicoSagaOrchestrator;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.DiagnosticStartPayload;
import com.oficina.os.domain.DomainException;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.SagaInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrdemServicoUseCase {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrdemServicoUseCase.class);

	private final OrdemServicoRepositoryPort ordensServico;
	private final HistoricoStatusRepositoryPort historicos;
	private final ClienteRepositoryPort clientes;
	private final VeiculoRepositoryPort veiculos;
	private final FilialRepositoryPort filiais;
	private final OrdemServicoSagaOrchestrator sagaOrchestrator;
	private final SagaRepositoryPort sagas;

	public OrdemServicoUseCase(OrdemServicoRepositoryPort ordensServico, HistoricoStatusRepositoryPort historicos,
			ClienteRepositoryPort clientes, VeiculoRepositoryPort veiculos, FilialRepositoryPort filiais,
			OrdemServicoSagaOrchestrator sagaOrchestrator, SagaRepositoryPort sagas) {
		this.ordensServico = ordensServico;
		this.historicos = historicos;
		this.clientes = clientes;
		this.veiculos = veiculos;
		this.filiais = filiais;
		this.sagaOrchestrator = sagaOrchestrator;
		this.sagas = sagas;
	}

	@Transactional
	public OrdemServico abrir(AbrirOrdemServicoCommand command) {
		try {
			Cliente cliente = clientes.findById(command.clienteId())
					.orElseThrow(() -> new ResourceNotFoundException("cliente not found"));
			var veiculo = veiculos.findById(command.veiculoId())
					.orElseThrow(() -> new ResourceNotFoundException("veiculo not found"));
			Filial filial = filiais.findById(command.filialId())
					.orElseThrow(() -> new ResourceNotFoundException("filial not found"));
			UUID correlationId = UUID.randomUUID();
			OrdemServico ordemServico = ordensServico.save(OrdemServico.abrir(cliente, veiculo, filial, command.observacoes()));
			historicos.save(ordemServico.registrarCriacao("Abertura da ordem de servico", correlationId));
			var saga = sagaOrchestrator.start(ordemServico.getId(), correlationId,
					new DiagnosticStartPayload(ordemServico.getClienteId(), ordemServico.getVeiculoId(),
							ordemServico.getFilialId(), ordemServico.getObservacoes()));
			LOGGER.info("saga started sagaId={} osId={} correlationId={}", saga.getId(), ordemServico.getId(),
					correlationId);
			return ordemServico;
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidInputException(ex.getMessage());
		}
		catch (DomainException ex) {
			throw new ConflictException(ex.getMessage());
		}
	}

	public OrdemServico buscar(UUID id) {
		return ordensServico.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("ordemServico not found"));
	}

	public PageResult<OrdemServico> pesquisar(OrdemServicoSearchCriteria criteria) {
		return ordensServico.search(criteria);
	}

	public List<HistoricoStatus> historico(UUID ordemServicoId) {
		buscar(ordemServicoId);
		return historicos.findByOrdemServicoId(ordemServicoId);
	}

	public SagaInstance saga(UUID ordemServicoId) {
		buscar(ordemServicoId);
		return sagas.findByOrdemServicoId(ordemServicoId)
				.orElseThrow(() -> new ResourceNotFoundException("saga not found"));
	}
}
