package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.authorization.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@Repository
public class OperationalAccessAdapter implements OperationalAccessPort {
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public OperationalAccessAdapter(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public boolean branch(UUID usuarioId, UUID filialId, Capability capability) {
        String table = capability == Capability.SAGA_INSPECT ? "administrative_grants" : "operational_grants";
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table
                + " WHERE usuario_id=? AND filial_id=? AND capability=? AND ativo)",
                Boolean.class, usuarioId, filialId, capability.name()));
    }

    @Override
    public boolean assigned(UUID usuarioId, UUID osId, Capability capability) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM mechanic_assignments
                WHERE usuario_id=? AND os_id=? AND capability=? AND ativo)
                """, Boolean.class, usuarioId, osId, capability.name()));
    }

    @Override
    public boolean administrative(UUID usuarioId, Capability capability) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM administrative_grants
                WHERE usuario_id=? AND capability=? AND filial_id IS NULL AND ativo)
                """, Boolean.class, usuarioId, capability.name()));
    }

    @Override
    public List<UUID> branches(UUID usuarioId, Capability capability) {
        return jdbc.queryForList("SELECT filial_id FROM operational_grants WHERE usuario_id=? AND capability=? AND ativo",
                UUID.class, usuarioId, capability.name());
    }

    @Override
    public boolean clientInBranch(UUID clienteId, UUID filialId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM cliente_filiais WHERE cliente_id=? AND filial_id=?)",
                Boolean.class, clienteId, filialId));
    }

    @Override
    public boolean vehicleInBranch(UUID veiculoId, UUID filialId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM veiculo_filiais WHERE veiculo_id=? AND filial_id=?)",
                Boolean.class, veiculoId, filialId));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void registerClient(UUID clienteId, UUID filialId) {
        entityManager.flush();
        jdbc.update("INSERT INTO cliente_filiais(cliente_id,filial_id) VALUES (?,?)", clienteId, filialId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void registerVehicle(UUID veiculoId, UUID filialId) {
        entityManager.flush();
        jdbc.update("INSERT INTO veiculo_filiais(veiculo_id,filial_id) VALUES (?,?)", veiculoId, filialId);
    }

    @Override
    public List<UUID> clients(UUID usuarioId) {
        return jdbc.queryForList("""
                SELECT DISTINCT cf.cliente_id FROM cliente_filiais cf
                JOIN operational_grants g ON g.filial_id=cf.filial_id
                WHERE g.usuario_id=? AND g.capability='CLIENT_READ' AND g.ativo
                ORDER BY cf.cliente_id
                """, UUID.class, usuarioId);
    }

    @Override
    public List<UUID> vehicles(UUID usuarioId, UUID clienteId) {
        return jdbc.queryForList("""
                SELECT DISTINCT vf.veiculo_id FROM veiculo_filiais vf
                JOIN veiculos v ON v.id=vf.veiculo_id
                JOIN operational_grants g ON g.filial_id=vf.filial_id
                WHERE g.usuario_id=? AND v.cliente_id=? AND g.capability='VEHICLE_READ' AND g.ativo
                ORDER BY vf.veiculo_id
                """, UUID.class, usuarioId, clienteId);
    }
}
