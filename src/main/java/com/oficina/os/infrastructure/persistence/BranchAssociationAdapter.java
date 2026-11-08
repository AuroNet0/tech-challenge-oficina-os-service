package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.authorization.BranchAssociationPort;
import com.oficina.os.application.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class BranchAssociationAdapter implements BranchAssociationPort {
    private final JdbcTemplate jdbc;

    public BranchAssociationAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean lockActiveBranch(UUID filialId) {
        return jdbc.query("SELECT ativo FROM filiais WHERE id=? FOR SHARE",
                (row, index) -> row.getBoolean("ativo"), filialId).stream().findFirst().orElse(false);
    }

    @Override
    public boolean lockActiveClient(UUID clienteId) {
        return jdbc.query("SELECT ativo FROM clientes WHERE id=? FOR SHARE",
                (row, index) -> row.getBoolean("ativo"), clienteId).stream().findFirst().orElse(false);
    }

    @Override
    public UUID lockVehicleOwner(UUID veiculoId) {
        return jdbc.query("SELECT cliente_id FROM veiculos WHERE id=? FOR SHARE",
                (row, index) -> row.getObject("cliente_id", UUID.class), veiculoId).stream().findFirst().orElse(null);
    }

    @Override
    public void associateClient(UUID actorId, UUID clienteId, UUID origemId, UUID destinoId, String motivo) {
        boolean created = jdbc.update("INSERT INTO cliente_filiais(cliente_id,filial_id) VALUES (?,?) ON CONFLICT (cliente_id,filial_id) DO NOTHING",
                clienteId, destinoId) == 1;
        audit(actorId, clienteId, null, origemId, destinoId, "CLIENT_ASSOCIATE", created, motivo);
    }

    @Override
    public void associateVehicle(UUID actorId, UUID clienteId, UUID veiculoId, UUID origemId, UUID destinoId, String motivo) {
        boolean created = jdbc.update("INSERT INTO veiculo_filiais(veiculo_id,filial_id) VALUES (?,?) ON CONFLICT (veiculo_id,filial_id) DO NOTHING",
                veiculoId, destinoId) == 1;
        audit(actorId, clienteId, veiculoId, origemId, destinoId, "VEHICLE_ASSOCIATE", created, motivo);
    }

    private void audit(UUID actorId, UUID clienteId, UUID veiculoId, UUID origemId, UUID destinoId,
                       String operation, boolean created, String motivo) {
        try {
            jdbc.update("""
                    INSERT INTO branch_association_audit(id,usuario_id,cliente_id,veiculo_id,origem_id,destino_id,operation,created,motivo)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), actorId, clienteId, veiculoId, origemId, destinoId, operation, created, motivo);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Association audit could not be recorded");
        }
    }
}
