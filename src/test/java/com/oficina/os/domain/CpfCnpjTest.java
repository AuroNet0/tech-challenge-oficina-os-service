package com.oficina.os.domain;

import com.oficina.os.domain.validation.CpfCnpj;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfCnpjTest {

	@Test
	void validatesCpfAndCnpjDigits() {
		assertThat(CpfCnpj.isValid("529.982.247-25")).isTrue();
		assertThat(CpfCnpj.isValid("04.252.011/0001-10")).isTrue();
	}

	@Test
	void rejectsInvalidOrRepeatedDocuments() {
		assertThat(CpfCnpj.isValid("529.982.247-24")).isFalse();
		assertThat(CpfCnpj.isValid("00.000.000/0000-00")).isFalse();
		assertThat(CpfCnpj.isValid("123")).isFalse();
		assertThat(CpfCnpj.isValid(null)).isFalse();
	}

	@Test
	void normalizesOnlyDigits() {
		assertThat(CpfCnpj.normalize("04.252.011/0001-10")).isEqualTo("04252011000110");
	}
}
