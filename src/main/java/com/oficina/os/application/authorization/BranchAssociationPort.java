package com.oficina.os.application.authorization;

import java.util.UUID;

public interface BranchAssociationPort {
    boolean lockActiveBranch(UUID filialId);
    boolean lockActiveClient(UUID clienteId);
    UUID lockVehicleOwner(UUID veiculoId);
    void associateClient(UUID actorId, UUID clienteId, UUID origemId, UUID destinoId, String motivo);
    void associateVehicle(UUID actorId, UUID clienteId, UUID veiculoId, UUID origemId, UUID destinoId, String motivo);
}
