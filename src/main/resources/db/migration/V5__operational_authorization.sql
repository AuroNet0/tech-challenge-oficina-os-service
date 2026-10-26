CREATE TABLE operational_grants (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    filial_id UUID NOT NULL REFERENCES filiais(id),
    capability VARCHAR(40) NOT NULL CHECK (capability IN ('OS_READ','OS_OPEN','CLIENT_READ','CLIENT_CREATE','VEHICLE_READ','VEHICLE_CREATE','BRANCH_READ')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    concedido_por UUID NOT NULL,
    concedido_em TIMESTAMPTZ NOT NULL,
    revogado_por UUID,
    revogado_em TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (usuario_id <> concedido_por),
    CHECK ((ativo AND revogado_por IS NULL AND revogado_em IS NULL) OR
           (NOT ativo AND revogado_por IS NOT NULL AND revogado_em IS NOT NULL AND revogado_em >= concedido_em))
);
CREATE UNIQUE INDEX uk_operational_grant_active ON operational_grants(usuario_id, filial_id, capability) WHERE ativo;
CREATE INDEX idx_operational_scope ON operational_grants(usuario_id, capability, filial_id) WHERE ativo;

CREATE TABLE mechanic_assignments (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    os_id UUID NOT NULL REFERENCES ordens_servico(id),
    capability VARCHAR(40) NOT NULL CHECK (capability IN ('OPERATIONAL_READ','VEHICLE_OPERATIONAL_READ')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    concedido_por UUID NOT NULL,
    concedido_em TIMESTAMPTZ NOT NULL,
    revogado_por UUID,
    revogado_em TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (usuario_id <> concedido_por),
    CHECK ((ativo AND revogado_por IS NULL AND revogado_em IS NULL) OR
           (NOT ativo AND revogado_por IS NOT NULL AND revogado_em IS NOT NULL AND revogado_em >= concedido_em))
);
CREATE UNIQUE INDEX uk_mechanic_assignment_active ON mechanic_assignments(usuario_id, os_id, capability) WHERE ativo;
CREATE INDEX idx_mechanic_scope ON mechanic_assignments(usuario_id, capability, os_id) WHERE ativo;

CREATE TABLE administrative_grants (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    capability VARCHAR(40) NOT NULL CHECK (capability IN ('BRANCH_CREATE','SAGA_INSPECT')),
    filial_id UUID REFERENCES filiais(id),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    concedido_por UUID NOT NULL,
    concedido_em TIMESTAMPTZ NOT NULL,
    revogado_por UUID,
    revogado_em TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (usuario_id <> concedido_por),
    CHECK ((capability = 'BRANCH_CREATE' AND filial_id IS NULL) OR (capability = 'SAGA_INSPECT' AND filial_id IS NOT NULL)),
    CHECK ((ativo AND revogado_por IS NULL AND revogado_em IS NULL) OR
           (NOT ativo AND revogado_por IS NOT NULL AND revogado_em IS NOT NULL AND revogado_em >= concedido_em))
);
CREATE UNIQUE INDEX uk_admin_branch_active ON administrative_grants(usuario_id, capability, filial_id) NULLS NOT DISTINCT WHERE ativo;

CREATE TABLE cliente_filiais (
    cliente_id UUID NOT NULL REFERENCES clientes(id),
    filial_id UUID NOT NULL REFERENCES filiais(id),
    PRIMARY KEY (cliente_id, filial_id)
);
CREATE INDEX idx_cliente_filiais_filial ON cliente_filiais(filial_id, cliente_id);
CREATE TABLE veiculo_filiais (
    veiculo_id UUID NOT NULL REFERENCES veiculos(id),
    filial_id UUID NOT NULL REFERENCES filiais(id),
    PRIMARY KEY (veiculo_id, filial_id)
);
CREATE INDEX idx_veiculo_filiais_filial ON veiculo_filiais(filial_id, veiculo_id);
