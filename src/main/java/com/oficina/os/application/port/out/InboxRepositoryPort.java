package com.oficina.os.application.port.out;

import com.oficina.os.domain.InboxMessage;

import java.util.Optional;
import java.util.UUID;

public interface InboxRepositoryPort {

	InboxMessage save(InboxMessage message);

	Optional<InboxMessage> findByEventIdForUpdate(UUID eventId);
}
