package com.oficina.os.application.port.out;

import com.oficina.os.domain.Veiculo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VeiculoRepositoryPort {

	Veiculo save(Veiculo veiculo);

	Optional<Veiculo> findById(UUID id);

	Optional<Veiculo> findByPlaca(String placa);

	List<Veiculo> findByClienteId(UUID clienteId);

	boolean existsByPlaca(String placa);
}
