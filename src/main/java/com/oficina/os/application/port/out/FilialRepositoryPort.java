package com.oficina.os.application.port.out;

import com.oficina.os.domain.Filial;

import java.util.Optional;
import java.util.UUID;

public interface FilialRepositoryPort {

	Filial save(Filial filial);

	Optional<Filial> findById(UUID id);

	Optional<Filial> findByCodigo(String codigo);

	boolean existsByCodigo(String codigo);
}
