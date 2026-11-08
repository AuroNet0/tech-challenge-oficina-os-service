package com.oficina.os.application.usecase;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import com.oficina.os.application.exception.ResourceNotFoundException;
import com.oficina.os.application.port.in.FilialCommand;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.domain.Filial;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FilialUseCase {

	private final FilialRepositoryPort filiais;

	public FilialUseCase(FilialRepositoryPort filiais) {
		this.filiais = filiais;
	}

	public Filial criar(FilialCommand command) {
		try {
			Filial filial = Filial.nova(command.nome(), command.codigo(), command.cidade(), command.uf());
			if (filiais.existsByCodigo(filial.getCodigo())) {
				throw new ConflictException("codigo already exists");
			}
			return filiais.save(filial);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidInputException(ex.getMessage());
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("codigo already exists");
		}
	}

	public Filial buscar(UUID id) {
		return filiais.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("filial not found"));
	}

	public List<Filial> listar() {
		return filiais.findAll();
	}
}
