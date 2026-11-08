package com.oficina.os.application.port.out;

import com.oficina.os.domain.Cliente;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ClienteRepositoryPort {

	Cliente save(Cliente cliente);

	Optional<Cliente> findById(UUID id);

	Optional<Cliente> findByCpfCnpj(String cpfCnpj);

	List<Cliente> findAll();

	boolean existsByCpfCnpj(String cpfCnpj);
}
