package com.oficina.os.security;

import com.oficina.os.application.authorization.*;
import com.oficina.os.application.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BranchAssociationUseCaseTest {
    final UUID user = UUID.randomUUID();
    final UUID client = UUID.randomUUID();
    final UUID origin = UUID.randomUUID();
    final UUID destination = UUID.randomUUID();
    final OperationalAccessPort access = mock(OperationalAccessPort.class);
    final BranchAssociationPort persistence = mock(BranchAssociationPort.class);
    final ResourceAuthorization policy = new ResourceAuthorization(access, mock(com.oficina.os.application.port.out.ClientAccessPort.class));
    final BranchAssociationUseCase service = new BranchAssociationUseCase(() -> new Actor(user, Set.of("ATENDENTE")), policy, access, persistence);

    @Test
    void deniesBeforePersistenceWhenDestinationOrOriginIsNotAuthorized() {
        assertThatThrownBy(() -> service.client(client, origin, destination, "Atendimento")).isInstanceOf(AuthorizationDenied.class);
        when(access.branch(user, destination, Capability.CLIENT_ASSOCIATE)).thenReturn(true);
        assertThatThrownBy(() -> service.client(client, origin, destination, "Atendimento")).isInstanceOf(AuthorizationDenied.class);
        verifyNoInteractions(persistence);
    }

    @Test
    void validatesReasonBeforeMutationEvenForNonHttpCallers() {
        when(access.branch(user, destination, Capability.CLIENT_ASSOCIATE)).thenReturn(true);
        when(access.branch(user, origin, Capability.CLIENT_READ)).thenReturn(true);
        when(access.clientInBranch(client, origin)).thenReturn(true);
        for (String reason : new String[]{null, " ", "a".repeat(201)}) {
            assertThatThrownBy(() -> service.client(client, origin, destination, reason)).isInstanceOf(InvalidInputException.class);
        }
        verifyNoInteractions(persistence);
    }
}
