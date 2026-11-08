package com.oficina.os.infrastructure.persistence;

import com.oficina.os.application.port.out.InboxRepositoryPort;
import com.oficina.os.domain.InboxMessage;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class InboxRepositoryAdapter implements InboxRepositoryPort {

	private final InboxMessageJpaRepository repository;

	InboxRepositoryAdapter(InboxMessageJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public InboxMessage save(InboxMessage message) {
		return toDomain(repository.save(toEntity(message)));
	}

	@Override
	public Optional<InboxMessage> findByEventIdForUpdate(UUID eventId) {
		return repository.findByEventIdForUpdate(eventId).map(this::toDomain);
	}

	private InboxMessageJpaEntity toEntity(InboxMessage message) {
		return new InboxMessageJpaEntity(message.getEventId(), message.getSagaId(), message.getEventType(),
				message.getPayload(), message.getStatus(), message.getReceivedAt(), message.getProcessedAt(),
				message.getLastError());
	}

	private InboxMessage toDomain(InboxMessageJpaEntity entity) {
		return InboxMessage.restore(entity.getEventId(), entity.getSagaId(), entity.getEventType(),
				entity.getPayload(), entity.getStatus(), entity.getReceivedAt(), entity.getProcessedAt(),
				entity.getLastError());
	}
}
