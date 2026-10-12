package com.oficina.os.domain.validation;

public final class CpfCnpj {

	private static final int[] CNPJ_FIRST_WEIGHTS = { 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };
	private static final int[] CNPJ_SECOND_WEIGHTS = { 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2 };

	private CpfCnpj() {
	}

	public static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("\\D", "");
	}

	public static boolean isValid(String value) {
		String digits = normalize(value);
		return switch (digits.length()) {
			case 11 -> isValidCpf(digits);
			case 14 -> isValidCnpj(digits);
			default -> false;
		};
	}

	private static boolean isValidCpf(String cpf) {
		if (hasOnlyRepeatedDigits(cpf)) {
			return false;
		}

		int digit1 = calculateCpfDigit(cpf, 9, 10);
		int digit2 = calculateCpfDigit(cpf, 10, 11);
		return digit1 == Character.getNumericValue(cpf.charAt(9))
				&& digit2 == Character.getNumericValue(cpf.charAt(10));
	}

	private static int calculateCpfDigit(String cpf, int length, int weightStart) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += Character.getNumericValue(cpf.charAt(i)) * (weightStart - i);
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}

	private static boolean isValidCnpj(String cnpj) {
		if (hasOnlyRepeatedDigits(cnpj)) {
			return false;
		}

		int digit1 = calculateCnpjDigit(cnpj, 12, CNPJ_FIRST_WEIGHTS);
		int digit2 = calculateCnpjDigit(cnpj, 13, CNPJ_SECOND_WEIGHTS);
		return digit1 == Character.getNumericValue(cnpj.charAt(12))
				&& digit2 == Character.getNumericValue(cnpj.charAt(13));
	}

	private static int calculateCnpjDigit(String cnpj, int length, int[] weights) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += Character.getNumericValue(cnpj.charAt(i)) * weights[i];
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}

	private static boolean hasOnlyRepeatedDigits(String value) {
		char first = value.charAt(0);
		for (int i = 1; i < value.length(); i++) {
			if (value.charAt(i) != first) {
				return false;
			}
		}
		return true;
	}
}
