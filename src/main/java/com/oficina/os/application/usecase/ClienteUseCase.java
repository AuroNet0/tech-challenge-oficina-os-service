package com.oficina.os.application.usecase;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import com.oficina.os.application.exception.ResourceNotFoundException;
import com.oficina.os.application.port.in.ClienteCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.domain.Cliente;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ClienteUseCase {

	private final ClienteRepositoryPort clientes;

	public ClienteUseCase(ClienteRepositoryPort clientes) {
		this.clientes = clientes;
	}

	public Cliente criar(ClienteCommand command) {
		try {
			Cliente cliente = Cliente.novo(command.nome(), command.cpfCnpj(), command.email(), command.telefone());
			if (clientes.existsByCpfCnpj(cliente.getCpfCnpj())) {
				throw new ConflictException("cpfCnpj already exists");
			}
			return clientes.save(cliente);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidInputException(ex.getMessage());
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("cpfCnpj already exists");
		}
	}

	public Cliente buscar(UUID id) {
		return clientes.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("cliente not found"));
	}

	public List<Cliente> listar() {
		return clientes.findAll();
	}
}
