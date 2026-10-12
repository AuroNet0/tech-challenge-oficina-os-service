package com.oficina.os.application.saga;

import java.util.List;

public record DiagnosticCompletedPayload(List<DiagnosticItem> items) {
	public record DiagnosticItem(String itemId, String descricao, String tipo, String quantidade, String unidadePreco,
			String precoPrevisto) {
	}
}
