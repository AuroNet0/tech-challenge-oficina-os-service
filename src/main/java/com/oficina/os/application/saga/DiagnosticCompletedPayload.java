package com.oficina.os.application.saga;

import java.util.List;

public record DiagnosticCompletedPayload(List<DiagnosticItem> items) {
	public record DiagnosticItem(String itemId, String descricao, String precoPrevisto) {
	}
}
