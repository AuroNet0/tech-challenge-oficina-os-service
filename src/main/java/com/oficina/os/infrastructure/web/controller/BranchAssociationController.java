package com.oficina.os.infrastructure.web.controller;

import com.oficina.os.application.authorization.BranchAssociationUseCase;
import com.oficina.os.infrastructure.web.request.AssociateClientRequest;
import com.oficina.os.infrastructure.web.request.AssociateVehicleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class BranchAssociationController {
    private final BranchAssociationUseCase useCase;

    BranchAssociationController(BranchAssociationUseCase useCase) { this.useCase = useCase; }

    @PutMapping("/clientes/{clienteId}/filiais/{filialId}")
    ResponseEntity<Void> client(@PathVariable UUID clienteId, @PathVariable UUID filialId,
                                @Valid @RequestBody AssociateClientRequest request) {
        useCase.client(clienteId, request.filialOrigemId(), filialId, request.motivo());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/veiculos/{veiculoId}/filiais/{filialId}")
    ResponseEntity<Void> vehicle(@PathVariable UUID veiculoId, @PathVariable UUID filialId,
                                 @Valid @RequestBody AssociateVehicleRequest request) {
        useCase.vehicle(veiculoId, request.clienteId(), request.filialOrigemId(), filialId, request.motivo());
        return ResponseEntity.noContent().build();
    }
}
