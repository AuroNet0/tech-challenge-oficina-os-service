package com.oficina.os.infrastructure.web.controller;

import com.oficina.os.application.port.in.ClienteCommand;
import com.oficina.os.application.authorization.AuthorizedBusinessUseCase;
import com.oficina.os.infrastructure.web.request.ClienteRequest;
import com.oficina.os.infrastructure.web.response.ClienteResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes")
class ClienteController {

	private final AuthorizedBusinessUseCase useCase;

	ClienteController(AuthorizedBusinessUseCase useCase) {
		this.useCase = useCase;
	}

	@Operation(summary = "Cadastrar cliente")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Cliente cadastrado"),
			@ApiResponse(responseCode = "400", description = "Dados invalidos"),
			@ApiResponse(responseCode = "409", description = "CPF/CNPJ duplicado")
	})
	@PostMapping
	ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request, @RequestParam UUID filialId) {
		var cliente = useCase.criarCliente(new ClienteCommand(request.nome(), request.cpfCnpj(), request.email(), request.telefone()), filialId);
		var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(cliente.getId()).toUri();
		return ResponseEntity.created(location).body(ClienteResponse.from(cliente));
	}

	@Operation(summary = "Buscar cliente por ID")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cliente encontrado"),
			@ApiResponse(responseCode = "404", description = "Cliente nao encontrado")
	})
	@GetMapping("/{id}")
	ClienteResponse buscar(@PathVariable UUID id) {
		return ClienteResponse.from(useCase.buscarCliente(id));
	}

	@Operation(summary = "Listar clientes")
	@GetMapping
	List<ClienteResponse> listar() {
		return useCase.listarClientes().stream().map(ClienteResponse::from).toList();
	}
}
