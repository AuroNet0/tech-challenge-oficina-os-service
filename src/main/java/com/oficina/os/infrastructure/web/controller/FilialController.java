package com.oficina.os.infrastructure.web.controller;

import com.oficina.os.application.port.in.FilialCommand;
import com.oficina.os.application.usecase.FilialUseCase;
import com.oficina.os.infrastructure.web.request.FilialRequest;
import com.oficina.os.infrastructure.web.response.FilialResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/filiais")
class FilialController {

	private final FilialUseCase useCase;

	FilialController(FilialUseCase useCase) {
		this.useCase = useCase;
	}

	@Operation(summary = "Cadastrar filial")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Filial cadastrada"),
			@ApiResponse(responseCode = "400", description = "Dados invalidos"),
			@ApiResponse(responseCode = "409", description = "Codigo duplicado")
	})
	@PostMapping
	ResponseEntity<FilialResponse> criar(@Valid @RequestBody FilialRequest request) {
		var filial = useCase.criar(new FilialCommand(request.nome(), request.codigo(), request.cidade(), request.uf()));
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(filial.getId()).toUri();
		return ResponseEntity.created(location).body(FilialResponse.from(filial));
	}

	@Operation(summary = "Buscar filial por ID")
	@GetMapping("/{id}")
	FilialResponse buscar(@PathVariable UUID id) {
		return FilialResponse.from(useCase.buscar(id));
	}

	@Operation(summary = "Listar filiais")
	@GetMapping
	List<FilialResponse> listar() {
		return useCase.listar().stream().map(FilialResponse::from).toList();
	}
}
