package com.oficina.os.domain.validation;

import java.util.Locale;

public final class TextNormalizer {

	private TextNormalizer() {
	}

	public static String required(String value, String field) {
		String normalized = optional(value);
		if (normalized == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return normalized;
	}

	public static String optional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public static String code(String value, String field) {
		String normalized = required(value, field).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
		if (normalized.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return normalized;
	}

	public static String uf(String value) {
		String uf = required(value, "uf").toUpperCase(Locale.ROOT);
		if (!uf.matches("^[A-Z]{2}$")) {
			throw new IllegalArgumentException("uf must have two letters");
		}
		return uf;
	}
}
