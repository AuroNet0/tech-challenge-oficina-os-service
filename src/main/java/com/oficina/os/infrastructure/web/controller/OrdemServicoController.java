package com.oficina.os.infrastructure.web.controller;

import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.OrdemServicoSearchCriteria;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import com.oficina.os.domain.OrdemServicoStatus;
import com.oficina.os.infrastructure.web.request.AbrirOrdemServicoRequest;
import com.oficina.os.infrastructure.web.response.HistoricoStatusResponse;
import com.oficina.os.infrastructure.web.response.OrdemServicoResponse;
import com.oficina.os.infrastructure.web.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/ordens-servico")
class OrdemServicoController {

	private final OrdemServicoUseCase useCase;

	OrdemServicoController(OrdemServicoUseCase useCase) {
		this.useCase = useCase;
	}

	@Operation(summary = "Abrir ordem de servico")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "OS aberta"),
			@ApiResponse(responseCode = "400", description = "Dados invalidos"),
			@ApiResponse(responseCode = "404", description = "Cliente, veiculo ou filial inexistente"),
			@ApiResponse(responseCode = "409", description = "Conflito de negocio")
	})
	@PostMapping
	ResponseEntity<OrdemServicoResponse> abrir(@Valid @RequestBody AbrirOrdemServicoRequest request) {
		var ordemServico = useCase.abrir(new AbrirOrdemServicoCommand(request.clienteId(), request.veiculoId(),
				request.filialId(), request.observacoes()));
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(ordemServico.getId()).toUri();
		return ResponseEntity.created(location).body(OrdemServicoResponse.from(ordemServico));
	}

	@Operation(summary = "Buscar OS por ID")
	@GetMapping("/{id}")
	OrdemServicoResponse buscar(@PathVariable UUID id) {
		return OrdemServicoResponse.from(useCase.buscar(id));
	}

	@Operation(summary = "Pesquisar ordens de servico")
	@GetMapping
	PageResponse<OrdemServicoResponse> pesquisar(
			@RequestParam(required = false) UUID clienteId,
			@RequestParam(required = false) UUID veiculoId,
			@RequestParam(required = false) UUID filialId,
			@RequestParam(required = false) OrdemServicoStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		var result = useCase.pesquisar(new OrdemServicoSearchCriteria(clienteId, veiculoId, filialId, status, page, size));
		return PageResponse.from(result, OrdemServicoResponse::from);
	}

	@Operation(summary = "Consultar historico de status da OS")
	@GetMapping("/{id}/historico")
	List<HistoricoStatusResponse> historico(@PathVariable UUID id) {
		return useCase.historico(id).stream().map(HistoricoStatusResponse::from).toList();
	}
}
