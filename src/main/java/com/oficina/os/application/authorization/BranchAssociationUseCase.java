package com.oficina.os.application.authorization;

import com.oficina.os.application.exception.ConflictException;
import com.oficina.os.application.exception.InvalidInputException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BranchAssociationUseCase {
    private final CurrentActor current;
    private final ResourceAuthorization authorization;
    private final OperationalAccessPort access;
    private final BranchAssociationPort associations;

    public BranchAssociationUseCase(CurrentActor current, ResourceAuthorization authorization,
                                    OperationalAccessPort access, BranchAssociationPort associations) {
        this.current = current;
        this.authorization = authorization;
        this.access = access;
        this.associations = associations;
    }

    @Transactional
    public void client(UUID clienteId, UUID origemId, UUID destinoId, String motivo) {
        var actor = current.get();
        authorizeClient(actor, clienteId, origemId, destinoId, Capability.CLIENT_ASSOCIATE);
        validateReason(motivo);
        lockActiveResources(clienteId, origemId, destinoId);
        associations.associateClient(actor.usuarioId(), clienteId, origemId, destinoId, motivo);
    }

    @Transactional
    public void vehicle(UUID veiculoId, UUID clienteId, UUID origemId, UUID destinoId, String motivo) {
        var actor = current.get();
        authorizeClient(actor, clienteId, origemId, destinoId, Capability.VEHICLE_ASSOCIATE);
        authorization.branch(actor, origemId, Capability.VEHICLE_READ, "ATENDENTE");
        if (!access.vehicleInBranch(veiculoId, origemId) || !access.clientInBranch(clienteId, destinoId)) {
            throw new AuthorizationDenied();
        }
        validateReason(motivo);
        lockActiveResources(clienteId, origemId, destinoId);
        if (!clienteId.equals(associations.lockVehicleOwner(veiculoId))) { throw new AuthorizationDenied(); }
        associations.associateVehicle(actor.usuarioId(), clienteId, veiculoId, origemId, destinoId, motivo);
    }

    private void authorizeClient(Actor actor, UUID clienteId, UUID origemId, UUID destinoId, Capability capability) {
        authorization.branch(actor, destinoId, capability, "ATENDENTE");
        authorization.branch(actor, origemId, Capability.CLIENT_READ, "ATENDENTE");
        if (!access.clientInBranch(clienteId, origemId)) { throw new AuthorizationDenied(); }
    }

    private void lockActiveResources(UUID clienteId, UUID origemId, UUID destinoId) {
        if (!associations.lockActiveBranch(origemId) || !associations.lockActiveBranch(destinoId)
                || !associations.lockActiveClient(clienteId)) {
            throw new ConflictException("Resources are not active for association");
        }
    }

    private void validateReason(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 200) {
            throw new InvalidInputException("Association reason is required and limited to 200 characters");
        }
    }
}
