package com.oficina.os.infrastructure.persistence;

import com.oficina.os.domain.Cliente;
import com.oficina.os.domain.Filial;
import com.oficina.os.domain.HistoricoStatus;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.domain.Veiculo;
import org.springframework.stereotype.Component;

@Component
class DomainPersistenceMapper {

	ClienteJpaEntity toEntity(Cliente cliente) {
		return new ClienteJpaEntity(cliente.getId(), cliente.getNome(), cliente.getCpfCnpj(), cliente.getEmail(),
				cliente.getTelefone(), cliente.isAtivo(), cliente.getCreatedAt(), cliente.getUpdatedAt());
	}

	Cliente toDomain(ClienteJpaEntity entity) {
		return Cliente.restaurar(entity.getId(), entity.getNome(), entity.getCpfCnpj(), entity.getEmail(),
				entity.getTelefone(), entity.isAtivo(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	VeiculoJpaEntity toEntity(Veiculo veiculo) {
		return new VeiculoJpaEntity(veiculo.getId(), veiculo.getClienteId(), veiculo.getPlaca(), veiculo.getMarca(),
				veiculo.getModelo(), veiculo.getAno(), veiculo.getCreatedAt(), veiculo.getUpdatedAt());
	}

	Veiculo toDomain(VeiculoJpaEntity entity) {
		return Veiculo.restaurar(entity.getId(), entity.getClienteId(), entity.getPlaca(), entity.getMarca(),
				entity.getModelo(), entity.getAno(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	FilialJpaEntity toEntity(Filial filial) {
		return new FilialJpaEntity(filial.getId(), filial.getNome(), filial.getCodigo(), filial.getCidade(),
				filial.getUf(), filial.isAtivo(), filial.getCreatedAt(), filial.getUpdatedAt());
	}

	Filial toDomain(FilialJpaEntity entity) {
		return Filial.restaurar(entity.getId(), entity.getNome(), entity.getCodigo(), entity.getCidade(),
				entity.getUf(), entity.isAtivo(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	OrdemServicoJpaEntity toEntity(OrdemServico ordemServico) {
		return new OrdemServicoJpaEntity(ordemServico.getId(), ordemServico.getClienteId(),
				ordemServico.getVeiculoId(), ordemServico.getFilialId(), ordemServico.getStatus(),
				ordemServico.getObservacoes(), ordemServico.getCreatedAt(), ordemServico.getUpdatedAt(),
				ordemServico.getStartedAt(), ordemServico.getFinishedAt(), ordemServico.getVersion());
	}

	OrdemServico toDomain(OrdemServicoJpaEntity entity) {
		return OrdemServico.restaurar(entity.getId(), entity.getClienteId(), entity.getVeiculoId(),
				entity.getFilialId(), entity.getStatus(), entity.getObservacoes(), entity.getCreatedAt(),
				entity.getUpdatedAt(), entity.getStartedAt(), entity.getFinishedAt(), entity.getVersion());
	}

	HistoricoStatusJpaEntity toEntity(HistoricoStatus historicoStatus) {
		return new HistoricoStatusJpaEntity(historicoStatus.getId(), historicoStatus.getOrdemServicoId(),
				historicoStatus.getStatusAnterior(), historicoStatus.getStatusNovo(), historicoStatus.getMotivo(),
				historicoStatus.getCreatedAt(), historicoStatus.getCorrelationId());
	}

	HistoricoStatus toDomain(HistoricoStatusJpaEntity entity) {
		return HistoricoStatus.restaurar(entity.getId(), entity.getOrdemServicoId(), entity.getStatusAnterior(),
				entity.getStatusNovo(), entity.getMotivo(), entity.getCreatedAt(), entity.getCorrelationId());
	}
}
