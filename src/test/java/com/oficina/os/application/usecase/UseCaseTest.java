package com.oficina.os.application.usecase;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import com.oficina.os.application.exception.ResourceNotFoundException;
import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.in.ClienteCommand;
import com.oficina.os.application.port.in.FilialCommand;
import com.oficina.os.application.port.in.VeiculoCommand;
import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UseCaseTest {

	@Test
	void createsClienteAndRejectsDuplicateOrInvalidInput() {
		ClienteRepositoryPort repository = mock(ClienteRepositoryPort.class);
		when(repository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ClienteUseCase useCase = new ClienteUseCase(repository);

		Cliente created = useCase.criar(new ClienteCommand("Maria", "529.982.247-25", null, null));

		assertThat(created.getCpfCnpj()).isEqualTo("52998224725");
		verify(repository).save(any(Cliente.class));

		when(repository.existsByCpfCnpj("52998224725")).thenReturn(true);
		assertThatThrownBy(() -> useCase.criar(new ClienteCommand("Maria", "529.982.247-25", null, null)))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> useCase.criar(new ClienteCommand("", "invalid", null, null)))
				.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> useCase.buscar(UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void createsVeiculoOnlyForExistingActiveClienteAndRejectsDuplicatePlate() {
		VeiculoRepositoryPort veiculos = mock(VeiculoRepositoryPort.class);
		ClienteRepositoryPort clientes = mock(ClienteRepositoryPort.class);
		Cliente cliente = Cliente.novo("Maria", "52998224725", null, null);
		when(clientes.findById(cliente.getId())).thenReturn(Optional.of(cliente));
		when(veiculos.save(any(Veiculo.class))).thenAnswer(invocation -> invocation.getArgument(0));
		VeiculoUseCase useCase = new VeiculoUseCase(veiculos, clientes);

		Veiculo created = useCase.criar(new VeiculoCommand(cliente.getId(), "abc-1d23", "Fiat", "Uno", 2020));

		assertThat(created.getPlaca()).isEqualTo("ABC1D23");

		when(veiculos.existsByPlaca("ABC1D23")).thenReturn(true);
		assertThatThrownBy(() -> useCase.criar(new VeiculoCommand(cliente.getId(), "abc-1d23", "Fiat", "Uno", 2020)))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> useCase.criar(new VeiculoCommand(UUID.randomUUID(), "ABC1234", "Fiat", "Uno", 2020)))
				.isInstanceOf(ResourceNotFoundException.class);

		Cliente inactive = Cliente.restaurar(UUID.randomUUID(), "Joao", "04252011000110", null, null,
				false, Instant.now(), Instant.now());
		when(clientes.findById(inactive.getId())).thenReturn(Optional.of(inactive));
		assertThatThrownBy(() -> useCase.criar(new VeiculoCommand(inactive.getId(), "DEF1234", "Fiat", "Uno", 2020)))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void createsFilialAndRejectsDuplicate() {
		FilialRepositoryPort repository = mock(FilialRepositoryPort.class);
		when(repository.save(any(Filial.class))).thenAnswer(invocation -> invocation.getArgument(0));
		FilialUseCase useCase = new FilialUseCase(repository);

		Filial created = useCase.criar(new FilialCommand("Centro", "sp-01", "Sao Paulo", "SP"));

		assertThat(created.getCodigo()).isEqualTo("SP01");

		when(repository.existsByCodigo("SP01")).thenReturn(true);
		assertThatThrownBy(() -> useCase.criar(new FilialCommand("Centro", "sp-01", "Sao Paulo", "SP")))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> useCase.buscar(UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void opensOrdemServicoAndRejectsMissingOrInvalidRelationships() {
		ClienteRepositoryPort clientes = mock(ClienteRepositoryPort.class);
		VeiculoRepositoryPort veiculos = mock(VeiculoRepositoryPort.class);
		FilialRepositoryPort filiais = mock(FilialRepositoryPort.class);
		OrdemServicoRepositoryPort ordens = mock(OrdemServicoRepositoryPort.class);
		HistoricoStatusRepositoryPort historicos = mock(HistoricoStatusRepositoryPort.class);
		OrdemServicoUseCase useCase = new OrdemServicoUseCase(ordens, historicos, clientes, veiculos, filiais);

		Cliente cliente = Cliente.novo("Maria", "52998224725", null, null);
		Veiculo veiculo = Veiculo.novo(cliente.getId(), "ABC1234", "Fiat", "Uno", 2020);
		Filial filial = Filial.nova("Centro", "SP01", "Sao Paulo", "SP");
		when(clientes.findById(cliente.getId())).thenReturn(Optional.of(cliente));
		when(veiculos.findById(veiculo.getId())).thenReturn(Optional.of(veiculo));
		when(filiais.findById(filial.getId())).thenReturn(Optional.of(filial));
		when(ordens.save(any(OrdemServico.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(historicos.save(any(HistoricoStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

		OrdemServico ordemServico = useCase.abrir(new AbrirOrdemServicoCommand(cliente.getId(), veiculo.getId(), filial.getId(), null));

		assertThat(ordemServico.getClienteId()).isEqualTo(cliente.getId());
		verify(historicos).save(any(HistoricoStatus.class));

		Veiculo otherVeiculo = Veiculo.novo(UUID.randomUUID(), "DEF1234", "Fiat", "Uno", 2020);
		when(veiculos.findById(otherVeiculo.getId())).thenReturn(Optional.of(otherVeiculo));
		assertThatThrownBy(() -> useCase.abrir(new AbrirOrdemServicoCommand(cliente.getId(), otherVeiculo.getId(), filial.getId(), null)))
				.isInstanceOf(ConflictException.class);
		assertThatThrownBy(() -> useCase.abrir(new AbrirOrdemServicoCommand(UUID.randomUUID(), veiculo.getId(), filial.getId(), null)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void rejectsOpeningOrdemServicoForInactiveClienteOrFilial() {
		ClienteRepositoryPort clientes = mock(ClienteRepositoryPort.class);
		VeiculoRepositoryPort veiculos = mock(VeiculoRepositoryPort.class);
		FilialRepositoryPort filiais = mock(FilialRepositoryPort.class);
		OrdemServicoRepositoryPort ordens = mock(OrdemServicoRepositoryPort.class);
		HistoricoStatusRepositoryPort historicos = mock(HistoricoStatusRepositoryPort.class);
		OrdemServicoUseCase useCase = new OrdemServicoUseCase(ordens, historicos, clientes, veiculos, filiais);

		Instant now = Instant.now();
		Cliente inactiveCliente = Cliente.restaurar(UUID.randomUUID(), "Maria", "52998224725", null, null,
				false, now, now);
		Filial filial = Filial.nova("Centro", "SP01", "Sao Paulo", "SP");
		Veiculo veiculo = Veiculo.novo(inactiveCliente.getId(), "ABC1234", "Fiat", "Uno", 2020);
		when(clientes.findById(inactiveCliente.getId())).thenReturn(Optional.of(inactiveCliente));
		when(veiculos.findById(veiculo.getId())).thenReturn(Optional.of(veiculo));
		when(filiais.findById(filial.getId())).thenReturn(Optional.of(filial));

		assertThatThrownBy(() -> useCase.abrir(new AbrirOrdemServicoCommand(inactiveCliente.getId(), veiculo.getId(), filial.getId(), null)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("cliente");

		Cliente activeCliente = Cliente.novo("Joao", "04252011000110", null, null);
		Veiculo activeVehicle = Veiculo.novo(activeCliente.getId(), "DEF1234", "Fiat", "Uno", 2020);
		Filial inactiveFilial = Filial.restaurar(UUID.randomUUID(), "Centro", "SP02", "Sao Paulo", "SP",
				false, now, now);
		when(clientes.findById(activeCliente.getId())).thenReturn(Optional.of(activeCliente));
		when(veiculos.findById(activeVehicle.getId())).thenReturn(Optional.of(activeVehicle));
		when(filiais.findById(inactiveFilial.getId())).thenReturn(Optional.of(inactiveFilial));

		assertThatThrownBy(() -> useCase.abrir(new AbrirOrdemServicoCommand(activeCliente.getId(), activeVehicle.getId(), inactiveFilial.getId(), null)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("filial");
	}
}
