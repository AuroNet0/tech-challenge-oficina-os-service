package com.oficina.os.application.usecase;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import com.oficina.os.application.exception.ResourceNotFoundException;
import com.oficina.os.application.port.in.VeiculoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Veiculo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VeiculoUseCase {

	private final VeiculoRepositoryPort veiculos;
	private final ClienteRepositoryPort clientes;

	public VeiculoUseCase(VeiculoRepositoryPort veiculos, ClienteRepositoryPort clientes) {
		this.veiculos = veiculos;
		this.clientes = clientes;
	}

	public Veiculo criar(VeiculoCommand command) {
		try {
			Cliente cliente = clientes.findById(command.clienteId())
					.orElseThrow(() -> new ResourceNotFoundException("cliente not found"));
			if (!cliente.isAtivo()) {
				throw new ConflictException("cliente is inactive");
			}
			Veiculo veiculo = Veiculo.novo(cliente.getId(), command.placa(), command.marca(), command.modelo(), command.ano());
			if (veiculos.existsByPlaca(veiculo.getPlaca())) {
				throw new ConflictException("placa already exists");
			}
			return veiculos.save(veiculo);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidInputException(ex.getMessage());
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("placa already exists");
		}
	}

	public Veiculo buscar(UUID id) {
		return veiculos.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("veiculo not found"));
	}

	public List<Veiculo> listarPorCliente(UUID clienteId) {
		buscarCliente(clienteId);
		return veiculos.findByClienteId(clienteId);
	}

	private void buscarCliente(UUID clienteId) {
		clientes.findById(clienteId)
				.orElseThrow(() -> new ResourceNotFoundException("cliente not found"));
	}
}
