package com.oficina.os.infrastructure.web.response;

import com.oficina.os.domain.Veiculo;

public record OperationalVehicleResponse(String placa, String marca, String modelo, Integer ano) {
    public static OperationalVehicleResponse from(Veiculo vehicle) {
        return new OperationalVehicleResponse(vehicle.getPlaca(), vehicle.getMarca(), vehicle.getModelo(), vehicle.getAno());
    }
}
