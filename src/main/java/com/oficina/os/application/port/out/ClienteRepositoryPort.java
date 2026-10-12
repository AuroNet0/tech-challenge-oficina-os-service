package com.oficina.os.application.port.out;

import com.oficina.os.domain.Cliente;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepositoryPort {

	Cliente save(Cliente cliente);

	Optional<Cliente> findById(UUID id);

	Optional<Cliente> findByCpfCnpj(String cpfCnpj);

	boolean existsByCpfCnpj(String cpfCnpj);
}
