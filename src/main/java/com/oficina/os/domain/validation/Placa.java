package com.oficina.os.domain.validation;

import java.util.Locale;

public final class Placa {

	private static final String OLD_FORMAT = "^[A-Z]{3}[0-9]{4}$";
	private static final String MERCOSUL_FORMAT = "^[A-Z]{3}[0-9][A-Z][0-9]{2}$";

	private Placa() {
	}

	public static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.trim()
				.toUpperCase(Locale.ROOT)
				.replaceAll("[^A-Z0-9]", "");
	}

	public static boolean isValid(String value) {
		String normalized = normalize(value);
		return normalized.matches(OLD_FORMAT) || normalized.matches(MERCOSUL_FORMAT);
	}
}
