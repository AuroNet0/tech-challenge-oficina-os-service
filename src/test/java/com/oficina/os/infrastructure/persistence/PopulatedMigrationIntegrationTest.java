package com.oficina.os.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PopulatedMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesPopulatedV4ToV5AndV6WithoutReclassifyingOrChangingBusinessRecords() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        var original = flyway("4");
        assertThat(original.migrate().migrationsExecuted).isEqualTo(4);
        original.validate();
        UUID client = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        jdbc.update("INSERT INTO clientes(id,nome,cpf_cnpj,ativo,created_at,updated_at) VALUES (?,'Legado','52998224725',true,now(),now())", client);
        jdbc.update("INSERT INTO filiais(id,nome,codigo,cidade,uf,ativo,created_at,updated_at) VALUES (?,'Origem','LEGADO','SP','SP',true,now(),now())", branch);
        jdbc.update("INSERT INTO veiculos(id,cliente_id,placa,modelo,created_at,updated_at) VALUES (?,?,'ABC1234','Uno',now(),now())", vehicle, client);
        jdbc.update("INSERT INTO ordens_servico(id,cliente_id,veiculo_id,filial_id,status,observacoes,created_at,updated_at) VALUES (?,?,?,?,'RECEBIDA','Historica',now(),now())", order, client, vehicle, branch);
        jdbc.update("INSERT INTO historico_status(id,ordem_servico_id,status_novo,created_at,correlation_id) VALUES (?,?,'RECEBIDA',now(),?)", UUID.randomUUID(), order, correlation);
        jdbc.update("INSERT INTO saga_instances(id,ordem_servico_id,status,current_step,correlation_id,created_at,updated_at) VALUES (?,?,'WAITING_DIAGNOSTIC','DIAGNOSTIC',?,now(),now())", saga, order, correlation);
        jdbc.update("INSERT INTO outbox_events(id,aggregate_id,aggregate_type,event_type,payload,status,created_at) VALUES (?,?,'OrdemServico','diagnostic.start.v1','{}'::jsonb,'PENDING',now())", UUID.randomUUID(), order);
        jdbc.update("INSERT INTO inbox_messages(event_id,saga_id,event_type,payload,status,received_at) VALUES (?,?,'diagnostic.started.v1','{}'::jsonb,'RECEIVED',now())", UUID.randomUUID(), saga);
        Map<String, List<String>> before = snapshot(jdbc);

        var version5 = flyway("5");
        assertThat(version5.migrate().migrationsExecuted).isEqualTo(1);
        version5.validate();
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertEmptyScopes(jdbc);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cliente_filiais VALUES (?,?)", UUID.randomUUID(), branch))
                .isInstanceOf(DataIntegrityViolationException.class);

        var latest = flyway("6");
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        latest.validate();
        assertThat(latest.migrate().migrationsExecuted).isZero();
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertEmptyScopes(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM branch_association_audit", Integer.class)).isZero();
        UUID actor = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        jdbc.update("INSERT INTO operational_grants(id,usuario_id,filial_id,capability,concedido_por,concedido_em) VALUES (?,?,?,'CLIENT_ASSOCIATE',?,now())", UUID.randomUUID(), actor, branch, operator);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO operational_grants(id,usuario_id,filial_id,capability,concedido_por,concedido_em) VALUES (?,?,?,'ALL',?,now())", UUID.randomUUID(), actor, branch, operator))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO cliente_filiais VALUES (?,?)", client, branch);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cliente_filiais VALUES (?,?)", client, branch))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    Flyway flyway(String version) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(version).load();
    }

    void assertEmptyScopes(JdbcTemplate jdbc) {
        for (String table : List.of("cliente_filiais", "veiculo_filiais", "operational_grants", "mechanic_assignments", "administrative_grants")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
    }

    Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
        var result = new LinkedHashMap<String, List<String>>();
        for (String table : List.of("clientes", "filiais", "veiculos", "ordens_servico", "historico_status", "saga_instances", "outbox_events", "inbox_messages")) {
            result.put(table, jdbc.queryForList("SELECT row_to_json(snapshot)::text FROM " + table + " snapshot ORDER BY 1", String.class));
        }
        return result;
    }
}
