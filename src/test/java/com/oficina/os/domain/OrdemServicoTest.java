package com.oficina.os.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemServicoTest {

	@Test
	void opensOrderInRecebidaAndRecordsCreationHistory() {
		Fixtures fixtures = fixtures();

		OrdemServico ordemServico = OrdemServico.abrir(fixtures.cliente(), fixtures.veiculo(), fixtures.filial(), "obs");
		HistoricoStatus historico = ordemServico.registrarCriacao("abertura", fixtures.correlationId());

		assertThat(ordemServico.getStatus()).isEqualTo(OrdemServicoStatus.RECEBIDA);
		assertThat(ordemServico.getClienteId()).isEqualTo(fixtures.cliente().getId());
		assertThat(historico.getStatusAnterior()).isNull();
		assertThat(historico.getStatusNovo()).isEqualTo(OrdemServicoStatus.RECEBIDA);
	}

	@Test
	void rejectsOpeningWhenRelationshipOrActiveRulesFail() {
		Fixtures fixtures = fixtures();
		Cliente inactiveCliente = Cliente.restaurar(UUID.randomUUID(), "Joao", "52998224725", null, null,
				false, Instant.now(), Instant.now());
		Filial inactiveFilial = Filial.restaurar(UUID.randomUUID(), "Centro", "SP01", "Sao Paulo", "SP",
				false, Instant.now(), Instant.now());

		assertThatThrownBy(() -> OrdemServico.abrir(fixtures.cliente(), fixtures.otherVeiculo(), fixtures.filial(), null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("veiculo");
		assertThatThrownBy(() -> OrdemServico.abrir(inactiveCliente, fixtures.veiculo(), fixtures.filial(), null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("cliente");
		assertThatThrownBy(() -> OrdemServico.abrir(fixtures.cliente(), fixtures.veiculo(), inactiveFilial, null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("filial");
	}

	@Test
	void followsNormalTransitionsAndSetsRelevantTimestamps() {
		Fixtures fixtures = fixtures();
		OrdemServico ordemServico = OrdemServico.abrir(fixtures.cliente(), fixtures.veiculo(), fixtures.filial(), null);

		assertThat(ordemServico.iniciarDiagnostico(null, null).getStatusNovo()).isEqualTo(OrdemServicoStatus.EM_DIAGNOSTICO);
		assertThat(ordemServico.aguardarAprovacao(null, null).getStatusNovo()).isEqualTo(OrdemServicoStatus.AGUARDANDO_APROVACAO);
		assertThat(ordemServico.iniciarExecucao(null, null).getStatusNovo()).isEqualTo(OrdemServicoStatus.EM_EXECUCAO);
		assertThat(ordemServico.getStartedAt()).isNotNull();
		assertThat(ordemServico.aguardarPagamento(null, null).getStatusNovo()).isEqualTo(OrdemServicoStatus.AGUARDANDO_PAGAMENTO);
		assertThat(ordemServico.finalizar(null, null).getStatusNovo()).isEqualTo(OrdemServicoStatus.FINALIZADA);
		assertThat(ordemServico.getFinishedAt()).isNotNull();
		assertThat(ordemServico.entregar(null, null).getStatusNovo()).isEqualTo(OrdemServicoStatus.ENTREGUE);
	}

	@Test
	void rejectsInvalidTransitionsAndDeliveryBeforeFinish() {
		Fixtures fixtures = fixtures();
		OrdemServico ordemServico = OrdemServico.abrir(fixtures.cliente(), fixtures.veiculo(), fixtures.filial(), null);

		assertThatThrownBy(() -> ordemServico.entregar(null, null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("invalid");
		assertThatThrownBy(() -> ordemServico.aguardarPagamento(null, null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("invalid");
	}

	@Test
	void allowsCancellationOnlyBeforeExecutionAndBlocksTerminalChanges() {
		Fixtures fixtures = fixtures();
		OrdemServico early = OrdemServico.abrir(fixtures.cliente(), fixtures.veiculo(), fixtures.filial(), null);

		HistoricoStatus cancellation = early.cancelar("cliente desistiu", null);

		assertThat(cancellation.getStatusNovo()).isEqualTo(OrdemServicoStatus.CANCELADA);
		assertThatThrownBy(() -> early.iniciarDiagnostico(null, null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("terminal");

		OrdemServico inExecution = OrdemServico.abrir(fixtures.cliente(), fixtures.veiculo(), fixtures.filial(), null);
		inExecution.iniciarDiagnostico(null, null);
		inExecution.aguardarAprovacao(null, null);
		inExecution.iniciarExecucao(null, null);

		assertThatThrownBy(() -> inExecution.cancelar("late", null))
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("cannot be cancelled");
	}

	private Fixtures fixtures() {
		Cliente cliente = Cliente.novo("Maria", "52998224725", null, null);
		Veiculo veiculo = Veiculo.novo(cliente.getId(), "ABC1234", "Fiat", "Uno", 2020);
		Veiculo otherVeiculo = Veiculo.novo(UUID.randomUUID(), "ABC1D23", "VW", "Gol", 2021);
		Filial filial = Filial.nova("Centro", "SP01", "Sao Paulo", "SP");
		return new Fixtures(cliente, veiculo, otherVeiculo, filial, UUID.randomUUID());
	}

	private record Fixtures(Cliente cliente, Veiculo veiculo, Veiculo otherVeiculo, Filial filial,
			UUID correlationId) {
	}
}
