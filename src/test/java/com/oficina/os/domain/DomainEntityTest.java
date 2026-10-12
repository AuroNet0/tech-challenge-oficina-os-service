package com.oficina.os.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEntityTest {

	@Test
	void createsClienteActiveWithNormalizedDocument() {
		Cliente cliente = Cliente.novo(" Maria ", "529.982.247-25", " maria@email.com ", " 11999999999 ");

		assertThat(cliente.getId()).isNotNull();
		assertThat(cliente.getNome()).isEqualTo("Maria");
		assertThat(cliente.getCpfCnpj()).isEqualTo("52998224725");
		assertThat(cliente.getEmail()).isEqualTo("maria@email.com");
		assertThat(cliente.isAtivo()).isTrue();
	}

	@Test
	void rejectsInvalidCliente() {
		assertThatThrownBy(() -> Cliente.novo("", "529.982.247-25", null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Cliente.novo("Maria", "111.111.111-11", null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createsVeiculoWithNormalizedPlateAndClienteRelationship() {
		UUID clienteId = UUID.randomUUID();

		Veiculo veiculo = Veiculo.novo(clienteId, " abc-1d23 ", " Fiat ", " Uno ", 2020);

		assertThat(veiculo.getPlaca()).isEqualTo("ABC1D23");
		assertThat(veiculo.getMarca()).isEqualTo("Fiat");
		assertThat(veiculo.getModelo()).isEqualTo("Uno");
		assertThat(veiculo.pertenceAoCliente(clienteId)).isTrue();
		assertThat(veiculo.pertenceAoCliente(UUID.randomUUID())).isFalse();
	}

	@Test
	void rejectsInvalidVeiculo() {
		assertThatThrownBy(() -> Veiculo.novo(UUID.randomUUID(), "invalid", "Fiat", "Uno", 2020))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Veiculo.novo(UUID.randomUUID(), "ABC1234", "Fiat", "", 2020))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Veiculo.novo(UUID.randomUUID(), "ABC1234", "Fiat", "Uno", 1800))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createsFilialActiveWithNormalizedCodeAndUf() {
		Filial filial = Filial.nova(" Centro ", " sp-01 ", " Sao Paulo ", " sp ");

		assertThat(filial.getCodigo()).isEqualTo("SP01");
		assertThat(filial.getUf()).isEqualTo("SP");
		assertThat(filial.isAtivo()).isTrue();
	}

	@Test
	void rejectsInvalidFilial() {
		assertThatThrownBy(() -> Filial.nova("Centro", "", "Sao Paulo", "SP"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Filial.nova("Centro", "---", "Sao Paulo", "SP"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Filial.nova("Centro", "SP01", "Sao Paulo", "SPO"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsUpdatedAtBeforeCreatedAtOnRestore() {
		Instant createdAt = Instant.parse("2026-01-02T00:00:00Z");
		Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

		assertThatThrownBy(() -> Cliente.restaurar(UUID.randomUUID(), "Maria", "52998224725", null, null,
				true, createdAt, updatedAt)).isInstanceOf(IllegalArgumentException.class);
	}
}
