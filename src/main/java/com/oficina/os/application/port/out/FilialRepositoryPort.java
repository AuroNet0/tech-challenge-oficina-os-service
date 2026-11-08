package com.oficina.os.application.port.out;

import com.oficina.os.domain.Filial;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface FilialRepositoryPort {

	Filial save(Filial filial);

	Optional<Filial> findById(UUID id);

	Optional<Filial> findByCodigo(String codigo);

	List<Filial> findAll();

	boolean existsByCodigo(String codigo);
}
