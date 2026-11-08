package com.oficina.os.security;

import com.oficina.os.application.authorization.*;
import com.oficina.os.application.exception.ClientAccessException;
import com.oficina.os.application.port.in.AbrirOrdemServicoCommand;
import com.oficina.os.application.port.out.*;
import com.oficina.os.application.usecase.*;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.infrastructure.identity.UnavailableClientAccessAdapter;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceAuthorizationTest {
    final UUID user = UUID.randomUUID();
    final UUID branch = UUID.randomUUID();
    final UUID osId = UUID.randomUUID();
    final OperationalAccessPort grants = mock(OperationalAccessPort.class);
    final ClientAccessPort clients = mock(ClientAccessPort.class);
    final ResourceAuthorization policy = new ResourceAuthorization(grants, clients);

    Actor actor(String... roles) { return new Actor(user, Set.of(roles)); }

    @Test
    void branchRolesRequireExplicitCapabilityAndNeverInherit() {
        for (String role : new String[]{"ATENDENTE", "GERENTE"}) {
            assertThatThrownBy(() -> policy.branch(actor(role), branch, Capability.OS_READ, "ATENDENTE", "GERENTE"))
                    .isInstanceOf(AuthorizationDenied.class);
            when(grants.branch(user, branch, Capability.OS_READ)).thenReturn(true);
            policy.branch(actor(role), branch, Capability.OS_READ, "ATENDENTE", "GERENTE");
            assertThatThrownBy(() -> policy.branch(actor(role), UUID.randomUUID(), Capability.OS_READ, "ATENDENTE", "GERENTE"))
                    .isInstanceOf(AuthorizationDenied.class);
            reset(grants);
        }
        assertThatThrownBy(() -> policy.role(actor(), "CLIENTE")).isInstanceOf(AuthorizationDenied.class);
        assertThatThrownBy(() -> policy.branch(actor("GERENTE"), branch, Capability.OS_OPEN, "ATENDENTE"))
                .isInstanceOf(AuthorizationDenied.class);
        verifyNoInteractions(grants);
    }

    @Test
    void mechanicRequiresOsAndSpecificCapability() {
        assertThatThrownBy(() -> policy.mechanic(actor("MECANICO"), osId, Capability.OPERATIONAL_READ))
                .isInstanceOf(AuthorizationDenied.class);
        when(grants.assigned(user, osId, Capability.OPERATIONAL_READ)).thenReturn(true);
        policy.mechanic(actor("MECANICO"), osId, Capability.OPERATIONAL_READ);
        assertThatThrownBy(() -> policy.mechanic(actor("MECANICO"), osId, Capability.VEHICLE_OPERATIONAL_READ))
                .isInstanceOf(AuthorizationDenied.class);
        assertThatThrownBy(() -> policy.mechanic(actor("GERENTE"), osId, Capability.OPERATIONAL_READ))
                .isInstanceOf(AuthorizationDenied.class);
    }

    @Test
    void clientRequiresFreshPositiveDecisionAndUnavailableIsNotDenial() {
        UUID clientId = UUID.randomUUID();
        assertThatThrownBy(() -> policy.client(actor("CLIENTE"), clientId)).isInstanceOf(AuthorizationDenied.class);
        when(clients.verificar(clientId)).thenReturn(true);
        policy.client(actor("CLIENTE"), clientId);
        policy.client(actor("CLIENTE"), clientId);
        verify(clients, times(3)).verificar(clientId);
        when(clients.verificar(clientId)).thenThrow(new ClientAccessException(ClientAccessException.Motivo.INDISPONIVEL));
        assertThatThrownBy(() -> policy.client(actor("CLIENTE"), clientId)).isInstanceOf(ClientAccessException.class);
        var unavailable = new ResourceAuthorization(grants, new UnavailableClientAccessAdapter());
        assertThatThrownBy(() -> unavailable.client(actor("CLIENTE"), clientId)).isInstanceOf(ClientAccessException.class);
        assertThatThrownBy(() -> policy.client(actor("GERENTE"), clientId)).isInstanceOf(AuthorizationDenied.class);
    }

    @Test
    void administrationRequiresSeparateGrant() {
        assertThatThrownBy(() -> policy.administrative(actor("GERENTE"), Capability.BRANCH_CREATE)).isInstanceOf(AuthorizationDenied.class);
        when(grants.administrative(user, Capability.BRANCH_CREATE)).thenReturn(true);
        policy.administrative(actor("GERENTE"), Capability.BRANCH_CREATE);
        assertThatThrownBy(() -> policy.administrative(actor("ATENDENTE"), Capability.BRANCH_CREATE)).isInstanceOf(AuthorizationDenied.class);
    }

    @Test
    void deniesBeforeAnyBusinessMutationAndDoesNotTrustSearchFilter() {
        var orders = mock(OrdemServicoUseCase.class);
        var query = mock(AuthorizedOrderQuery.class);
        var service = new AuthorizedBusinessUseCase(() -> actor("ATENDENTE"), policy, grants, query,
                mock(ClienteUseCase.class), mock(VeiculoUseCase.class), mock(FilialUseCase.class), orders);
        var command = new AbrirOrdemServicoCommand(UUID.randomUUID(), UUID.randomUUID(), branch, null);
        assertThatThrownBy(() -> service.abrir(command)).isInstanceOf(AuthorizationDenied.class);
        verifyNoInteractions(orders);
        var criteria = new OrdemServicoSearchCriteria(null, null, UUID.randomUUID(), null, 0, 10);
        service.pesquisar(criteria);
        verify(query).search(criteria, actor("ATENDENTE"));
        verifyNoInteractions(orders);
    }
}
