ALTER TABLE operational_grants DROP CONSTRAINT operational_grants_capability_check;
ALTER TABLE operational_grants ADD CONSTRAINT operational_grants_capability_check
    CHECK (capability IN ('OS_READ','OS_OPEN','CLIENT_READ','CLIENT_CREATE','VEHICLE_READ',
                         'VEHICLE_CREATE','BRANCH_READ','CLIENT_ASSOCIATE','VEHICLE_ASSOCIATE'));

CREATE TABLE branch_association_audit (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    veiculo_id UUID REFERENCES veiculos(id),
    origem_id UUID NOT NULL REFERENCES filiais(id),
    destino_id UUID NOT NULL REFERENCES filiais(id),
    operation VARCHAR(30) NOT NULL CHECK (operation IN ('CLIENT_ASSOCIATE','VEHICLE_ASSOCIATE')),
    created BOOLEAN NOT NULL,
    motivo VARCHAR(200) NOT NULL CHECK (length(trim(motivo)) BETWEEN 1 AND 200),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((operation='CLIENT_ASSOCIATE' AND veiculo_id IS NULL) OR
           (operation='VEHICLE_ASSOCIATE' AND veiculo_id IS NOT NULL))
);
CREATE INDEX idx_association_audit_client ON branch_association_audit(cliente_id, occurred_at);
CREATE INDEX idx_association_audit_vehicle ON branch_association_audit(veiculo_id, occurred_at) WHERE veiculo_id IS NOT NULL;
