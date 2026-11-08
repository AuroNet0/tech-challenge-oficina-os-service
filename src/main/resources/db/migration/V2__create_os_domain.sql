CREATE TABLE clientes (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    cpf_cnpj VARCHAR(14) NOT NULL,
    email VARCHAR(255),
    telefone VARCHAR(255),
    ativo BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_clientes_cpf_cnpj UNIQUE (cpf_cnpj)
);

CREATE TABLE filiais (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    codigo VARCHAR(255) NOT NULL,
    cidade VARCHAR(255) NOT NULL,
    uf VARCHAR(2) NOT NULL,
    ativo BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_filiais_codigo UNIQUE (codigo)
);

CREATE TABLE veiculos (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL,
    placa VARCHAR(255) NOT NULL,
    marca VARCHAR(255),
    modelo VARCHAR(255) NOT NULL,
    ano INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_veiculos_placa UNIQUE (placa),
    CONSTRAINT fk_veiculos_clientes FOREIGN KEY (cliente_id) REFERENCES clientes (id),
    CONSTRAINT ck_veiculos_ano CHECK (ano IS NULL OR (ano >= 1886 AND ano <= 2100))
);

CREATE TABLE ordens_servico (
    id UUID PRIMARY KEY,
    cliente_id UUID NOT NULL,
    veiculo_id UUID NOT NULL,
    filial_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    observacoes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ordens_servico_clientes FOREIGN KEY (cliente_id) REFERENCES clientes (id),
    CONSTRAINT fk_ordens_servico_veiculos FOREIGN KEY (veiculo_id) REFERENCES veiculos (id),
    CONSTRAINT fk_ordens_servico_filiais FOREIGN KEY (filial_id) REFERENCES filiais (id),
    CONSTRAINT ck_ordens_servico_status CHECK (
        status IN (
            'RECEBIDA',
            'EM_DIAGNOSTICO',
            'AGUARDANDO_APROVACAO',
            'EM_EXECUCAO',
            'AGUARDANDO_PAGAMENTO',
            'FINALIZADA',
            'ENTREGUE',
            'CANCELADA'
        )
    )
);

CREATE TABLE historico_status (
    id UUID PRIMARY KEY,
    ordem_servico_id UUID NOT NULL,
    status_anterior VARCHAR(255),
    status_novo VARCHAR(255) NOT NULL,
    motivo TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID,
    CONSTRAINT fk_historico_status_ordens_servico FOREIGN KEY (ordem_servico_id) REFERENCES ordens_servico (id),
    CONSTRAINT ck_historico_status_status_anterior CHECK (
        status_anterior IS NULL OR status_anterior IN (
            'RECEBIDA',
            'EM_DIAGNOSTICO',
            'AGUARDANDO_APROVACAO',
            'EM_EXECUCAO',
            'AGUARDANDO_PAGAMENTO',
            'FINALIZADA',
            'ENTREGUE',
            'CANCELADA'
        )
    ),
    CONSTRAINT ck_historico_status_status_novo CHECK (
        status_novo IN (
            'RECEBIDA',
            'EM_DIAGNOSTICO',
            'AGUARDANDO_APROVACAO',
            'EM_EXECUCAO',
            'AGUARDANDO_PAGAMENTO',
            'FINALIZADA',
            'ENTREGUE',
            'CANCELADA'
        )
    )
);

CREATE INDEX idx_veiculos_cliente_id ON veiculos (cliente_id);
CREATE INDEX idx_ordens_servico_cliente_id ON ordens_servico (cliente_id);
CREATE INDEX idx_ordens_servico_veiculo_id ON ordens_servico (veiculo_id);
CREATE INDEX idx_ordens_servico_filial_id ON ordens_servico (filial_id);
CREATE INDEX idx_ordens_servico_status ON ordens_servico (status);
CREATE INDEX idx_historico_status_ordem_servico_id ON historico_status (ordem_servico_id);
