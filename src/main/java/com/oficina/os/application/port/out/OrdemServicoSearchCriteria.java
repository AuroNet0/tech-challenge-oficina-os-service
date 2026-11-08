package com.oficina.os.application.port.out;

import com.oficina.os.domain.OrdemServicoStatus;

import java.util.UUID;

public record OrdemServicoSearchCriteria(
		UUID clienteId,
		UUID veiculoId,
		UUID filialId,
		OrdemServicoStatus status,
		int page,
		int size) {

	public OrdemServicoSearchCriteria {
		if (page < 0) {
			throw new IllegalArgumentException("page must not be negative");
		}
		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("size must be between 1 and 100");
		}
	}
}
