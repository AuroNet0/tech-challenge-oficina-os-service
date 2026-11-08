package com.oficina.os.security;

import com.oficina.os.application.authorization.*;
import com.oficina.os.application.port.out.*;
import com.oficina.os.application.usecase.*;
import com.oficina.os.application.port.in.*;
import com.oficina.os.domain.*;
import com.oficina.os.application.saga.SagaMessageSerializer;
import com.oficina.os.application.saga.SagaEventType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "oficina.outbox.publisher.enabled=false")
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final TestJwtIssuer JWT = new TestJwtIssuer();
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("oficina.security.jwt.jwks-uri", JWT::jwksUri);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @AfterAll
    static void close() { JWT.close(); }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClienteUseCase clients;
    @Autowired VeiculoUseCase vehicles;
    @Autowired FilialUseCase branches;
    @Autowired OrdemServicoUseCase orders;
    @Autowired OperationalAccessPort access;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired RabbitTemplate rabbit;
    @Autowired SagaMessageSerializer serializer;
    @Autowired SagaRepositoryPort sagas;
    @Autowired OutboxEventRepositoryPort outbox;
    @Autowired MessageBrokerPort broker;
    UUID user;
    UUID other;
    Filial branch;
    Filial outside;
    Cliente client;
    Veiculo vehicle;
    OrdemServico order;
    OrdemServico hidden;

    @BeforeEach
    void fixture() {
        user = UUID.randomUUID();
        other = UUID.randomUUID();
        branch = branches.criar(new FilialCommand("Centro", UUID.randomUUID().toString(), "SP", "SP"));
        outside = branches.criar(new FilialCommand("Outra", UUID.randomUUID().toString(), "SP", "SP"));
        client = clients.listar().stream().findFirst().orElseGet(() -> clients.criar(new ClienteCommand("Maria", "52998224725", "private@example.test", "11999999999")));
        vehicle = vehicles.listarPorCliente(client.getId()).stream().findFirst().orElseGet(() -> vehicles.criar(new VeiculoCommand(client.getId(), "ABC1234", "Fiat", "Uno", 2020)));
        access.registerClient(client.getId(), branch.getId());
        access.registerVehicle(vehicle.getId(), branch.getId());
        order = orders.abrir(new AbrirOrdemServicoCommand(client.getId(), vehicle.getId(), branch.getId(), "Private notes"));
        hidden = orders.abrir(new AbrirOrdemServicoCommand(client.getId(), vehicle.getId(), outside.getId(), "Other private notes"));
    }

    String bearer(String... roles) { return "Bearer " + JWT.token(user, roles); }

    UUID grant(UUID actor, UUID filial, String capability) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO operational_grants(id,usuario_id,filial_id,capability,concedido_por,concedido_em) VALUES (?,?,?,?,?,now())", id, actor, filial, capability, other);
        return id;
    }

    UUID assignment(String capability) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mechanic_assignments(id,usuario_id,os_id,capability,concedido_por,concedido_em) VALUES (?,?,?,?,?,now())", id, user, order.getId(), capability, other);
        return id;
    }

    @Test
    void signedJwtHttpBoundaryAndNoImplicitPrivileges() throws Exception {
        mvc.perform(get("/api/v1/ordens-servico")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ordens-servico").header("Authorization", bearer("GERENTE"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/filiais").header("Authorization", bearer("GERENTE"))
                .contentType("application/json").content("{\"nome\":\"A\",\"codigo\":\"A\",\"cidade\":\"SP\",\"uf\":\"SP\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ordens-servico/{id}/saga", order.getId()).header("Authorization", bearer("GERENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/permissions").header("Authorization", bearer("GERENTE")).header("X-Roles", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs").header("Authorization", bearer("GERENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void branchReadsAndPaginationCannotBeExpandedWithIdsOrHeaders() throws Exception {
        grant(user, branch.getId(), "OS_READ");
        for (String role : new String[]{"ATENDENTE", "GERENTE"}) {
            mvc.perform(get("/api/v1/ordens-servico/{id}", order.getId()).header("Authorization", bearer(role)))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/ordens-servico/{id}", hidden.getId()).header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/ordens-servico").header("Authorization", bearer(role)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(order.getId().toString()));
            mvc.perform(get("/api/v1/ordens-servico").header("Authorization", bearer(role))
                    .header("X-User-Id", other.toString()).param("filialId", outside.getId().toString())
                    .param("usuarioId", other.toString()).param("sort", "clienteId"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", "Bearer " + JWT.token(other, "ATENDENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        grant(user, branch.getId(), "BRANCH_READ");
        mvc.perform(get("/api/v1/filiais").header("Authorization", bearer("GERENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/filiais/{id}", branch.getId()).header("Authorization", bearer("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/filiais/{id}", outside.getId()).header("Authorization", bearer("GERENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void mechanicSeesOnlyAssignedOperationalProjectionAndRevocationIsObserved() throws Exception {
        mvc.perform(get("/api/v1/ordens-servico/{id}/operacional", order.getId()).header("Authorization", bearer("MECANICO")))
                .andExpect(status().isForbidden());
        UUID id = assignment("OPERATIONAL_READ");
        mvc.perform(get("/api/v1/ordens-servico/{id}/operacional", order.getId()).header("Authorization", bearer("MECANICO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").doesNotExist()).andExpect(jsonPath("$.observacoes").doesNotExist());
        mvc.perform(get("/api/v1/ordens-servico/{id}/veiculo-operacional", order.getId()).header("Authorization", bearer("MECANICO")))
                .andExpect(status().isForbidden());
        assignment("VEHICLE_OPERATIONAL_READ");
        mvc.perform(get("/api/v1/ordens-servico/{id}/veiculo-operacional", order.getId()).header("Authorization", bearer("MECANICO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelo").value("Uno")).andExpect(jsonPath("$.clienteId").doesNotExist());
        mvc.perform(get("/api/v1/clientes/{id}", client.getId()).header("Authorization", bearer("MECANICO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ordens-servico/{id}", order.getId()).header("Authorization", bearer("MECANICO"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE mechanic_assignments SET ativo=false,revogado_por=?,revogado_em=now(),version=version+1 WHERE id=? AND version=0", other, id);
        mvc.perform(get("/api/v1/ordens-servico/{id}/operacional", order.getId()).header("Authorization", bearer("MECANICO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ordens-servico/{id}/operacional", hidden.getId()).header("Authorization", bearer("MECANICO"))).andExpect(status().isForbidden());
    }

    @Test
    void customerResourcesStayUnavailableUntilTrustedAuthChannelExists() throws Exception {
        mvc.perform(get("/api/v1/ordens-servico/{id}", order.getId()).header("Authorization", bearer("CLIENTE")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("Authorization dependency unavailable"));
        mvc.perform(get("/api/v1/ordens-servico/{id}/historico", order.getId()).header("Authorization", bearer("CLIENTE")))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/ordens-servico").param("clienteId", client.getId().toString()).header("Authorization", bearer("CLIENTE")))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", bearer("CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/veiculos/{id}", vehicle.getId()).header("Authorization", bearer("CLIENTE"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/veiculos").param("clienteId", client.getId().toString()).header("Authorization", bearer("CLIENTE")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void deniedWriteCreatesNoOsHistorySagaOrOutboxAndCannotChangeScope() throws Exception {
        grant(user, branch.getId(), "OS_OPEN");
        String body = "{\"clienteId\":\"%s\",\"veiculoId\":\"%s\",\"filialId\":\"%s\"}"
                .formatted(client.getId(), vehicle.getId(), outside.getId());
        long before = businessCount();
        mvc.perform(post("/api/v1/ordens-servico").header("Authorization", bearer("ATENDENTE"))
                .header("X-Filial-Id", branch.getId()).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertThat(businessCount()).isEqualTo(before);
        mvc.perform(post("/api/v1/ordens-servico").header("Authorization", bearer("CLIENTE"))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertThat(businessCount()).isEqualTo(before);
    }

    @Test
    void internalCertificateRejectionPreservesUnauthorizedStatusWithoutMutation() throws Exception {
        long before = businessCount();
        String body = """
                {"usuarioId":"%s","osId":"%s","capability":"DIAGNOSTIC_READ","correlationId":"%s"}
                """.formatted(user, order.getId(), UUID.randomUUID());
        mvc.perform(post("/internal/technical-authorization").secure(true).header("Authorization", bearer("MECANICO"))
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(businessCount()).isEqualTo(before);
    }

    long businessCount() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM ordens_servico)+(SELECT count(*) FROM historico_status)+(SELECT count(*) FROM saga_instances)+(SELECT count(*) FROM outbox_events)", Long.class);
    }

    @Test
    void constraintsIndexesRollbackAndConcurrentRevocation() throws Exception {
        UUID id = grant(user, branch.getId(), "OS_READ");
        assertThat(access.branch(user, branch.getId(), Capability.OS_READ)).isTrue();
        assertThatThrownBy(() -> grant(user, branch.getId(), "OS_READ")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> grant(other, branch.getId(), "OS_READ")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> grant(user, branch.getId(), "ALL")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE operational_grants SET ativo=false,revogado_por=? WHERE id=?", other, id))
                .isInstanceOf(DataIntegrityViolationException.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("UPDATE operational_grants SET ativo=false,revogado_por=?,revogado_em=now(),version=version+1 WHERE id=? AND version=0", other, id);
            status.setRollbackOnly();
        });
        assertThat(access.branch(user, branch.getId(), Capability.OS_READ)).isTrue();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> revoke = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return jdbc.update("UPDATE operational_grants SET ativo=false,revogado_por=?,revogado_em=now(),version=version+1 WHERE id=? AND version=0", other, id);
            };
            var first = executor.submit(revoke);
            var second = executor.submit(revoke);
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(access.branch(user, branch.getId(), Capability.OS_READ)).isFalse();
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", bearer("ATENDENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname IN ('idx_operational_scope','idx_mechanic_scope','uk_admin_branch_active')", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='5' AND success", Integer.class)).isEqualTo(1);
    }

    @Test
    void attendantCatalogReadAndAdministrativeSagaAreExplicitlyScoped() throws Exception {
        grant(user, branch.getId(), "CLIENT_READ");
        grant(user, branch.getId(), "VEHICLE_READ");
        mvc.perform(get("/api/v1/clientes").header("Authorization", bearer("ATENDENTE"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/clientes/{id}", client.getId()).header("Authorization", bearer("ATENDENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/veiculos").param("clienteId", client.getId().toString()).header("Authorization", bearer("ATENDENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/veiculos/{id}", vehicle.getId()).header("Authorization", bearer("ATENDENTE"))).andExpect(status().isOk());
        jdbc.update("INSERT INTO administrative_grants(id,usuario_id,capability,filial_id,concedido_por,concedido_em) VALUES (?,?,'SAGA_INSPECT',?,?,now())",
                UUID.randomUUID(), user, branch.getId(), other);
        mvc.perform(get("/api/v1/ordens-servico/{id}/saga", order.getId()).header("Authorization", bearer("GERENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ordens-servico/{id}/saga", hidden.getId()).header("Authorization", bearer("GERENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void realRabbitConsumerAdvancesSagaAndDeduplicatesWithoutHttpIdentity() throws Exception {
        var saga = sagas.findByOrdemServicoId(order.getId()).orElseThrow();
        UUID commandId = jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=? AND event_type='diagnostic.start.v1'", UUID.class, order.getId());
        var command = outbox.findById(commandId).orElseThrow();
        assertThat(command.getPayload()).doesNotContain("userAccessToken", "Authorization", "Bearer");
        broker.publish(command);
        assertThat(rabbit.receive("execution.diagnostic.start", 5000)).isNotNull();
        var expired = JWT.claims(user, "ATENDENTE");
        long past = java.time.Instant.now().getEpochSecond() - 1000;
        expired.put("iat", past); expired.put("nbf", past); expired.put("exp", past + 900);
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", "Bearer " + JWT.sign(expired, JWT.key, com.nimbusds.jose.JWSAlgorithm.RS256)))
                .andExpect(status().isUnauthorized());
        String started = serializer.serializeCommand(UUID.randomUUID(), SagaEventType.DIAGNOSTIC_STARTED,
                saga.getCorrelationId(), saga.getId(), order.getId(), Map.of());
        rabbit.convertAndSend("oficina.events", SagaEventType.DIAGNOSTIC_STARTED, started);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orders.buscar(order.getId()).getStatus()).isEqualTo(OrdemServicoStatus.EM_DIAGNOSTICO));
        long history = jdbc.queryForObject("SELECT count(*) FROM historico_status WHERE ordem_servico_id=?", Long.class, order.getId());
        rabbit.convertAndSend("oficina.events", SagaEventType.DIAGNOSTIC_STARTED, started);
        String completed = serializer.serializeCommand(UUID.randomUUID(), SagaEventType.DIAGNOSTIC_COMPLETED,
                saga.getCorrelationId(), saga.getId(), order.getId(), Map.of("items", List.of(Map.of("itemId", "1", "descricao", "Peca", "tipo", "SERVICO", "quantidade", "1", "unidadePreco", "UNIDADE", "precoPrevisto", "10.00"))));
        rabbit.convertAndSend("oficina.events", SagaEventType.DIAGNOSTIC_COMPLETED, completed);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(sagas.findByOrdemServicoId(order.getId()).orElseThrow().getStatus()).isEqualTo(SagaStatus.WAITING_QUOTE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historico_status WHERE ordem_servico_id=?", Long.class, order.getId())).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_messages WHERE saga_id=? AND status='PROCESSED'", Integer.class, saga.getId())).isEqualTo(2);
    }
}
