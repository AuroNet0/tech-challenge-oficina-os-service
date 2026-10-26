package com.oficina.os.security;

import com.oficina.os.application.authorization.OperationalAccessPort;
import com.oficina.os.application.port.in.*;
import com.oficina.os.application.usecase.*;
import com.oficina.os.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"oficina.outbox.publisher.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
class BranchAssociationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final TestJwtIssuer JWT = new TestJwtIssuer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("oficina.security.jwt.jwks-uri", JWT::jwksUri);
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
    UUID user;
    Filial origin;
    Filial destination;
    Cliente client;
    Veiculo vehicle;
    OrdemServico historical;

    @BeforeEach
    void fixture() {
        user = UUID.randomUUID();
        origin = branches.criar(new FilialCommand("Origem", UUID.randomUUID().toString(), "SP", "SP"));
        destination = branches.criar(new FilialCommand("Destino", UUID.randomUUID().toString(), "SP", "SP"));
        client = clients.listar().stream().filter(candidate -> candidate.getCpfCnpj().equals("52998224725")).findFirst()
                .orElseGet(() -> clients.criar(new ClienteCommand("Maria", "52998224725", "private@example.test", "11999999999")));
        vehicle = vehicles.listarPorCliente(client.getId()).stream().findFirst()
                .orElseGet(() -> vehicles.criar(new VeiculoCommand(client.getId(), "ABC1234", "Fiat", "Uno", 2020)));
        access.registerClient(client.getId(), origin.getId());
        access.registerVehicle(vehicle.getId(), origin.getId());
        grant(origin.getId(), "CLIENT_READ");
        grant(origin.getId(), "VEHICLE_READ");
        grant(destination.getId(), "CLIENT_ASSOCIATE");
        grant(destination.getId(), "VEHICLE_ASSOCIATE");
        historical = orders.abrir(new AbrirOrdemServicoCommand(client.getId(), vehicle.getId(), origin.getId(), "Historica"));
    }

    void grant(UUID branch, String capability) {
        jdbc.update("INSERT INTO operational_grants(id,usuario_id,filial_id,capability,concedido_por,concedido_em) VALUES (?,?,?,?,?,now())",
                UUID.randomUUID(), user, branch, capability, UUID.randomUUID());
    }

    String token(String... roles) { return "Bearer " + JWT.token(user, roles); }

    MockHttpServletRequestBuilder associateClient(UUID clientId) {
        return put("/api/v1/clientes/{id}/filiais/{filial}", clientId, destination.getId())
                .header("Authorization", token("ATENDENTE")).contentType("application/json")
                .content("{\"filialOrigemId\":\"%s\",\"motivo\":\"Atendimento entre filiais\"}".formatted(origin.getId()));
    }

    MockHttpServletRequestBuilder associateVehicle(UUID vehicleId, UUID ownerId) {
        return put("/api/v1/veiculos/{id}/filiais/{filial}", vehicleId, destination.getId())
                .header("Authorization", token("ATENDENTE")).contentType("application/json")
                .content("{\"clienteId\":\"%s\",\"filialOrigemId\":\"%s\",\"motivo\":\"Atendimento entre filiais\"}".formatted(ownerId, origin.getId()));
    }

    long audits() {
        return jdbc.queryForObject("SELECT count(*) FROM branch_association_audit WHERE usuario_id=?", Long.class, user);
    }

    @Test
    void associatesExistingRecordsOpensNewOsAndKeepsHistoryAndPaginationScoped() throws Exception {
        long clientsBefore = jdbc.queryForObject("SELECT count(*) FROM clientes", Long.class);
        long vehiclesBefore = jdbc.queryForObject("SELECT count(*) FROM veiculos", Long.class);
        mvc.perform(associateClient(client.getId())).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isNoContent());
        assertThat(access.clientInBranch(client.getId(), origin.getId())).isTrue();
        assertThat(access.clientInBranch(client.getId(), destination.getId())).isTrue();
        assertThat(access.vehicleInBranch(vehicle.getId(), origin.getId())).isTrue();
        assertThat(access.vehicleInBranch(vehicle.getId(), destination.getId())).isTrue();
        assertThat(vehicles.buscar(vehicle.getId()).getClienteId()).isEqualTo(client.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clientes", Long.class)).isEqualTo(clientsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veiculos", Long.class)).isEqualTo(vehiclesBefore);
        grant(destination.getId(), "OS_OPEN");
        grant(destination.getId(), "OS_READ");
        mvc.perform(post("/api/v1/ordens-servico").header("Authorization", token("ATENDENTE")).contentType("application/json")
                .content("{\"clienteId\":\"%s\",\"veiculoId\":\"%s\",\"filialId\":\"%s\"}".formatted(client.getId(), vehicle.getId(), destination.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.filialId").value(destination.getId().toString()));
        assertThat(orders.buscar(historical.getId()).getFilialId()).isEqualTo(origin.getId());
        assertThat(orders.historico(historical.getId())).hasSize(1);
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", token("ATENDENTE")).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].filialId").value(destination.getId().toString()));
        mvc.perform(get("/api/v1/ordens-servico").header("Authorization", token("ATENDENTE")).param("filialId", origin.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(audits()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saga_instances WHERE ordem_servico_id IN (SELECT id FROM ordens_servico WHERE filial_id IN (?,?))", Integer.class, origin.getId(), destination.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id IN (SELECT id FROM ordens_servico WHERE filial_id IN (?,?)) AND event_type='diagnostic.start.v1'", Integer.class, origin.getId(), destination.getId())).isEqualTo(2);
    }

    @Test
    void repeatedRequestsKeepOneAssociationAndAuditEachAcceptedOperation() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(associateClient(client.getId())).andExpect(status().isNoContent());
            mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente_filiais WHERE cliente_id=? AND filial_id=?", Integer.class, client.getId(), destination.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM veiculo_filiais WHERE veiculo_id=? AND filial_id=?", Integer.class, vehicle.getId(), destination.getId())).isEqualTo(1);
        assertThat(audits()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_association_audit WHERE usuario_id=? AND created", Integer.class, user)).isEqualTo(2);
    }

    @Test
    void concurrentRequestsCreateOnlyOneAssociationOfEachType() throws Exception {
        race(() -> mvc.perform(associateClient(client.getId())).andExpect(status().isNoContent()));
        race(() -> mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isNoContent()));
        assertThat(audits()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_association_audit WHERE usuario_id=? AND created", Integer.class, user)).isEqualTo(2);
    }

    void race(Callable<?> operation) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> task = () -> { barrier.await(10, TimeUnit.SECONDS); return operation.call(); };
            var first = executor.submit(task);
            var second = executor.submit(task);
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void destinationNeedsAssociationCapabilityAndNeverAcceptsCreateAsSubstitute() throws Exception {
        jdbc.update("DELETE FROM operational_grants WHERE usuario_id=? AND filial_id=?", user, destination.getId());
        grant(destination.getId(), "CLIENT_CREATE");
        grant(destination.getId(), "VEHICLE_CREATE");
        mvc.perform(associateClient(client.getId())).andExpect(status().isForbidden());
        mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isForbidden());
        assertThat(audits()).isZero();
        assertThat(access.clientInBranch(client.getId(), destination.getId())).isFalse();
    }

    @Test
    void rejectsMissingJwtAndWrongRoles() throws Exception {
        for (String role : new String[]{"GERENTE", "MECANICO", "CLIENTE"}) {
            mvc.perform(put("/api/v1/clientes/{id}/filiais/{filial}", client.getId(), destination.getId())
                    .header("Authorization", token(role)).contentType("application/json")
                    .content("{\"filialOrigemId\":\"%s\",\"motivo\":\"Atendimento\"}".formatted(origin.getId())))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/v1/clientes/{id}/filiais/{filial}", client.getId(), destination.getId())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/clientes/{id}/filiais/{filial}", client.getId(), destination.getId())
                .header("Authorization", token()).contentType("application/json")
                .content("{\"filialOrigemId\":\"%s\",\"motivo\":\"Atendimento\"}".formatted(origin.getId())))
                .andExpect(status().isForbidden());
        assertThat(audits()).isZero();
    }

    @Test
    void unknownHiddenAndLegacyIdsAreDeniedUniformlyWithoutLookupOrPii() throws Exception {
        mvc.perform(associateClient(UUID.randomUUID())).andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Access denied"));
        jdbc.update("DELETE FROM cliente_filiais WHERE cliente_id=? AND filial_id=?", client.getId(), origin.getId());
        mvc.perform(associateClient(client.getId())).andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Access denied"));
        mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/clientes").header("Authorization", token("ATENDENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/clientes/por-cpf").param("cpf", client.getCpfCnpj()).header("Authorization", token("ATENDENTE")))
                .andExpect(status().isBadRequest());
        assertThat(audits()).isZero();
    }

    @Test
    void rejectsOwnerMismatchAndRequiresClientAssociationAtDestinationFirst() throws Exception {
        mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isForbidden());
        var other = clients.listar().stream().filter(candidate -> candidate.getCpfCnpj().equals("12345678909")).findFirst()
                .orElseGet(() -> clients.criar(new ClienteCommand("Outro", "12345678909", null, null)));
        access.registerClient(other.getId(), origin.getId());
        access.registerClient(other.getId(), destination.getId());
        mvc.perform(associateVehicle(vehicle.getId(), other.getId())).andExpect(status().isForbidden());
        mvc.perform(associateVehicle(UUID.randomUUID(), other.getId())).andExpect(status().isForbidden());
        assertThat(access.vehicleInBranch(vehicle.getId(), destination.getId())).isFalse();
        assertThat(vehicles.buscar(vehicle.getId()).getClienteId()).isEqualTo(client.getId());
        assertThat(audits()).isZero();
    }

    @Test
    void requiresOriginReadAndActiveResourcesAndValidReason() throws Exception {
        jdbc.update("UPDATE clientes SET ativo=false WHERE id=?", client.getId());
        try { mvc.perform(associateClient(client.getId())).andExpect(status().isConflict()); }
        finally { jdbc.update("UPDATE clientes SET ativo=true WHERE id=?", client.getId()); }
        jdbc.update("UPDATE filiais SET ativo=false WHERE id=?", destination.getId());
        mvc.perform(associateClient(client.getId())).andExpect(status().isConflict());
        jdbc.update("UPDATE filiais SET ativo=true WHERE id=?", destination.getId());
        mvc.perform(put("/api/v1/clientes/{id}/filiais/{filial}", client.getId(), destination.getId())
                .header("Authorization", token("ATENDENTE")).contentType("application/json")
                .content("{\"filialOrigemId\":\"%s\",\"motivo\":\"\"}".formatted(origin.getId())))
                .andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM operational_grants WHERE usuario_id=? AND filial_id=? AND capability='CLIENT_READ'", user, origin.getId());
        mvc.perform(associateClient(client.getId())).andExpect(status().isForbidden());
        assertThat(audits()).isZero();
    }

    @Test
    void auditFailureRollsBackAssociation() throws Exception {
        jdbc.execute("ALTER TABLE branch_association_audit ADD CONSTRAINT test_audit_failure CHECK (usuario_id <> '" + user + "'::uuid)");
        try {
            mvc.perform(associateClient(client.getId())).andExpect(status().isConflict());
            assertThat(access.clientInBranch(client.getId(), destination.getId())).isFalse();
            assertThat(audits()).isZero();
            access.registerClient(client.getId(), destination.getId());
            mvc.perform(associateVehicle(vehicle.getId(), client.getId())).andExpect(status().isConflict());
            assertThat(access.vehicleInBranch(vehicle.getId(), destination.getId())).isFalse();
            assertThat(audits()).isZero();
        } finally { jdbc.execute("ALTER TABLE branch_association_audit DROP CONSTRAINT test_audit_failure"); }
    }
}
