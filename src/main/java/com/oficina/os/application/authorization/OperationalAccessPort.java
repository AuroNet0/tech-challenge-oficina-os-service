package com.oficina.os.application.authorization;

import java.util.List;
import java.util.UUID;

public interface OperationalAccessPort {
    boolean branch(UUID usuarioId, UUID filialId, Capability capability);
    boolean assigned(UUID usuarioId, UUID osId, Capability capability);
    boolean administrative(UUID usuarioId, Capability capability);
    List<UUID> branches(UUID usuarioId, Capability capability);
    boolean clientInBranch(UUID clienteId, UUID filialId);
    boolean vehicleInBranch(UUID veiculoId, UUID filialId);
    void registerClient(UUID clienteId, UUID filialId);
    void registerVehicle(UUID veiculoId, UUID filialId);
    List<UUID> clients(UUID usuarioId);
    List<UUID> vehicles(UUID usuarioId, UUID clienteId);
}
