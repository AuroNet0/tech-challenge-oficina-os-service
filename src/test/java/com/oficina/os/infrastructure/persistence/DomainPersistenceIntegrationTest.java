package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.ClienteRepositoryPort;
import com.oficina.os.application.port.out.FilialRepositoryPort;
import com.oficina.os.application.port.out.HistoricoStatusRepositoryPort;
import com.oficina.os.application.port.out.OrdemServicoRepositoryPort;
import com.oficina.os.application.port.out.VeiculoRepositoryPort;
import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.OrdemServicoStatus;
import com.oficina.os.domain.Veiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class DomainPersistenceIntegrationTest {

	private static final AtomicInteger DOCUMENT_SEQUENCE = new AtomicInteger(1000);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("oficina_os_domain_test")
			.withUsername("oficina_os")
			.withPassword("oficina_os");

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private ClienteRepositoryPort clientes;

	@Autowired
	private VeiculoRepositoryPort veiculos;

	@Autowired
	private FilialRepositoryPort filiais;

	@Autowired
	private OrdemServicoRepositoryPort ordensServico;

	@Autowired
	private HistoricoStatusRepositoryPort historicos;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void flywayRunsV2AndHibernateValidatesSchema() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name IN ('clientes', 'veiculos', 'filiais', 'ordens_servico', 'historico_status')
				""", Integer.class);

		Integer migrationCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM flyway_schema_history
				WHERE script = 'V2__create_os_domain.sql'
				  AND success = true
				""", Integer.class);

		assertThat(tableCount).isEqualTo(5);
		assertThat(migrationCount).isEqualTo(1);
	}

	@Test
	void persistsAndRecoversDomainEntitiesAndHistory() {
		Fixture fixture = persistedFixture("ABC1234", "SP-PIN");
		OrdemServico ordemServico = ordensServico.save(
				OrdemServico.abrir(fixture.cliente(), fixture.veiculo(), fixture.filial(), "Primeira OS"));
		HistoricoStatus creation = historicos.save(ordemServico.registrarCriacao("Criacao", fixture.correlationId()));
		HistoricoStatus transition = historicos.save(ordemServico.iniciarDiagnostico("Triagem", fixture.correlationId()));
		ordemServico = ordensServico.save(ordemServico);

		assertThat(clientes.findByCpfCnpj(fixture.cliente().getCpfCnpj()))
				.map(Cliente::getId)
				.contains(fixture.cliente().getId());
		assertThat(veiculos.findByPlaca("abc-1234")).map(Veiculo::getId).contains(fixture.veiculo().getId());
		assertThat(filiais.findByCodigo("sp-pin")).map(Filial::getId).contains(fixture.filial().getId());
		assertThat(ordensServico.findById(ordemServico.getId()))
				.map(OrdemServico::getStatus)
				.contains(OrdemServicoStatus.EM_DIAGNOSTICO);
		assertThat(ordensServico.findByClienteId(fixture.cliente().getId())).hasSize(1);
		assertThat(ordensServico.findByVeiculoId(fixture.veiculo().getId())).hasSize(1);
		assertThat(ordensServico.findByFilialId(fixture.filial().getId())).hasSize(1);
		assertThat(ordensServico.findByStatus(OrdemServicoStatus.EM_DIAGNOSTICO)).extracting(OrdemServico::getId)
				.contains(ordemServico.getId());
		assertThat(historicos.findByOrdemServicoId(ordemServico.getId()))
				.extracting(HistoricoStatus::getId)
				.containsExactly(creation.getId(), transition.getId());
	}

	@Test
	void enforcesUniqueConstraints() {
		Cliente cliente = clientes.save(Cliente.novo("Maria", nextCpf(), null, null));
		Filial filial = filiais.save(Filial.nova("Centro", "SP-UNI-" + UUID.randomUUID(), "Sao Paulo", "SP"));
		veiculos.save(Veiculo.novo(cliente.getId(), "DEF1234", "Fiat", "Uno", 2020));

		assertThatThrownBy(() -> clientes.save(Cliente.novo("Outra", cliente.getCpfCnpj(), null, null)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> filiais.save(Filial.nova("Outra", filial.getCodigo(), "Sao Paulo", "SP")))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> veiculos.save(Veiculo.novo(cliente.getId(), "DEF-1234", "VW", "Gol", 2021)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enforcesForeignKeys() {
		assertThatThrownBy(() -> veiculos.save(Veiculo.novo(UUID.randomUUID(), "GHI1234", "Fiat", "Uno", 2020)))
				.isInstanceOf(DataIntegrityViolationException.class);

		OrdemServico orphanOrder = OrdemServico.restaurar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), OrdemServicoStatus.RECEBIDA, null, Instant.now(), Instant.now(), null, null, null);

		assertThatThrownBy(() -> ordensServico.save(orphanOrder))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void updatesOrderWithOptimisticVersion() {
		Fixture fixture = persistedFixture("JKL1234", "SP-OPT-" + UUID.randomUUID());
		OrdemServico saved = ordensServico.save(OrdemServico.abrir(fixture.cliente(), fixture.veiculo(), fixture.filial(), null));

		OrdemServico firstCopy = copy(saved);
		OrdemServico secondCopy = copy(saved);

		firstCopy.iniciarDiagnostico("primeiro", null);
		OrdemServico updated = ordensServico.save(firstCopy);

		secondCopy.iniciarDiagnostico("segundo", null);

		assertThat(updated.getVersion()).isEqualTo(1L);
		assertThatThrownBy(() -> ordensServico.save(secondCopy))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private Fixture persistedFixture(String placa, String codigoFilial) {
		Cliente cliente = clientes.save(Cliente.novo("Maria", nextCpf(), "maria@email.com", "11999999999"));
		Veiculo veiculo = veiculos.save(Veiculo.novo(cliente.getId(), placa, "Fiat", "Uno", 2020));
		Filial filial = filiais.save(Filial.nova("Centro", codigoFilial, "Sao Paulo", "SP"));
		return new Fixture(cliente, veiculo, filial, UUID.randomUUID());
	}

	private OrdemServico copy(OrdemServico ordemServico) {
		return OrdemServico.restaurar(ordemServico.getId(), ordemServico.getClienteId(), ordemServico.getVeiculoId(),
				ordemServico.getFilialId(), ordemServico.getStatus(), ordemServico.getObservacoes(),
				ordemServico.getCreatedAt(), ordemServico.getUpdatedAt(), ordemServico.getStartedAt(),
				ordemServico.getFinishedAt(), ordemServico.getVersion());
	}

	private String nextCpf() {
		String base = String.format("%09d", DOCUMENT_SEQUENCE.incrementAndGet());
		return base + cpfCheckDigits(base);
	}

	private String cpfCheckDigits(String firstNineDigits) {
		int first = cpfDigit(firstNineDigits, 10);
		int second = cpfDigit(firstNineDigits + first, 11);
		return Integer.toString(first) + second;
	}

	private int cpfDigit(String digits, int weightStart) {
		int sum = 0;
		for (int i = 0; i < digits.length(); i++) {
			sum += Character.getNumericValue(digits.charAt(i)) * (weightStart - i);
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}

	private record Fixture(Cliente cliente, Veiculo veiculo, Filial filial, UUID correlationId) {
	}
}
