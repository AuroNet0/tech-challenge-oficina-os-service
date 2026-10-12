package com.oficina.os.infrastructure.web.controller;

import com.oficina.os.application.port.in.VeiculoCommand;
import com.oficina.os.application.usecase.VeiculoUseCase;
import com.oficina.os.infrastructure.web.request.VeiculoRequest;
import com.oficina.os.infrastructure.web.response.VeiculoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/veiculos")
class VeiculoController {

	private final VeiculoUseCase useCase;

	VeiculoController(VeiculoUseCase useCase) {
		this.useCase = useCase;
	}

	@Operation(summary = "Cadastrar veiculo")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Veiculo cadastrado"),
			@ApiResponse(responseCode = "400", description = "Dados invalidos"),
			@ApiResponse(responseCode = "404", description = "Cliente nao encontrado"),
			@ApiResponse(responseCode = "409", description = "Placa duplicada ou cliente inativo")
	})
	@PostMapping
	ResponseEntity<VeiculoResponse> criar(@Valid @RequestBody VeiculoRequest request) {
		var veiculo = useCase.criar(new VeiculoCommand(request.clienteId(), request.placa(), request.marca(),
				request.modelo(), request.ano()));
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(veiculo.getId()).toUri();
		return ResponseEntity.created(location).body(VeiculoResponse.from(veiculo));
	}

	@Operation(summary = "Buscar veiculo por ID")
	@GetMapping("/{id}")
	VeiculoResponse buscar(@PathVariable UUID id) {
		return VeiculoResponse.from(useCase.buscar(id));
	}

	@Operation(summary = "Listar veiculos de um cliente")
	@GetMapping
	List<VeiculoResponse> listar(@RequestParam @NotNull UUID clienteId) {
		return useCase.listarPorCliente(clienteId).stream().map(VeiculoResponse::from).toList();
	}
}
