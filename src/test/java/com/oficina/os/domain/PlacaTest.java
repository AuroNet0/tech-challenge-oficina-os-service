package com.oficina.os.domain;

import com.oficina.os.domain.validation.Placa;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlacaTest {

	@Test
	void validatesBrazilianOldAndMercosulFormats() {
		assertThat(Placa.isValid("ABC-1234")).isTrue();
		assertThat(Placa.isValid("abc 1d23")).isTrue();
	}

	@Test
	void rejectsInvalidPlates() {
		assertThat(Placa.isValid("AB-1234")).isFalse();
		assertThat(Placa.isValid("ABC-12D4")).isFalse();
		assertThat(Placa.isValid(null)).isFalse();
	}

	@Test
	void normalizesPlate() {
		assertThat(Placa.normalize(" abc-1d23 ")).isEqualTo("ABC1D23");
	}
}
