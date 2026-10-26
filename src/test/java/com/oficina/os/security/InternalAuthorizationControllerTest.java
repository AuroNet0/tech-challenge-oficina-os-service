package com.oficina.os.security;

import com.oficina.os.application.authorization.*;
import com.oficina.os.application.port.out.ClientAccessPort;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import com.oficina.os.domain.OrdemServico;
import com.oficina.os.infrastructure.web.InternalAuthorizationController;
import com.oficina.os.infrastructure.web.InternalAuthorizationController.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalAuthorizationControllerTest {
    private final OrdemServicoUseCase orders = mock(OrdemServicoUseCase.class);
    private final OperationalAccessPort grants = mock(OperationalAccessPort.class);
    private final ClientAccessPort clients = mock(ClientAccessPort.class);
    private final InternalAuthorizationController controller = new InternalAuthorizationController(orders, new ResourceAuthorization(grants, clients));
    private final UUID userId = UUID.randomUUID();
    private final UUID osId = UUID.randomUUID();

    private JwtAuthenticationToken user(String role) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("unit-test").header("alg", "RS256").subject(userId.toString()).build(),
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MockHttpServletRequest service(String name) {
        var request = new MockHttpServletRequest();
        request.setSecure(true);
        var certificate = mock(X509Certificate.class);
        when(certificate.getSubjectX500Principal()).thenReturn(new X500Principal("CN=" + name));
        when(certificate.getBasicConstraints()).thenReturn(-1);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{certificate});
        return request;
    }

    private TechnicalRequest technical(UUID actor, Capability capability) {
        return new TechnicalRequest(actor, osId, capability, UUID.randomUUID());
    }

    @Test
    void certificateRequiredBeforeResourceLookup() {
        var request = new MockHttpServletRequest();
        request.setSecure(true);
        var error = assertThrows(ResponseStatusException.class, () -> controller.technical(technical(userId, Capability.DIAGNOSTIC_READ), user("MECANICO"), request));
        assertEquals(401, error.getStatusCode().value());
        verifyNoInteractions(orders, grants, clients);
    }

    @Test
    void serviceIdentityAndUserMustMatch() {
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.technical(technical(userId, Capability.DIAGNOSTIC_READ), user("MECANICO"), service("billing-service"))).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.technical(technical(UUID.randomUUID(), Capability.DIAGNOSTIC_READ), user("MECANICO"), service("execution-service"))).getStatusCode().value());
        verifyNoInteractions(orders, grants, clients);
    }

    @Test
    void onlyExplicitTechnicalCapabilitiesAreEligible() {
        assertThrows(ResponseStatusException.class, () -> controller.technical(technical(userId, Capability.OS_READ), user("MECANICO"), service("execution-service")));
        assertThrows(ResponseStatusException.class, () -> controller.technical(technical(userId, null), user("MECANICO"), service("execution-service")));
        verifyNoInteractions(orders, grants, clients);
    }

    @Test
    void readDoesNotGrantWriteAndResponseIsCorrelated() {
        when(grants.assigned(userId, osId, Capability.DIAGNOSTIC_READ)).thenReturn(true);
        var read = technical(userId, Capability.DIAGNOSTIC_READ);
        var decision = controller.technical(read, user("MECANICO"), service("execution-service"));
        assertEquals("no-store", decision.getHeaders().getCacheControl());
        assertEquals(new TechnicalDecision(userId, osId, read.capability(), read.correlationId(), true), decision.getBody());
        assertFalse(controller.technical(technical(userId, Capability.DIAGNOSTIC_WRITE), user("MECANICO"), service("execution-service")).getBody().allowed());
    }

    @Test
    void eligibleCapabilityDoesNotBypassRoleOrRevocation() {
        when(grants.assigned(userId, osId, Capability.REPAIR_COMPLETE)).thenReturn(true, false);
        assertFalse(controller.technical(technical(userId, Capability.REPAIR_COMPLETE), user("ATENDENTE"), service("execution-service")).getBody().allowed());
        assertTrue(controller.technical(technical(userId, Capability.REPAIR_COMPLETE), user("MECANICO"), service("execution-service")).getBody().allowed());
        assertFalse(controller.technical(technical(userId, Capability.REPAIR_COMPLETE), user("MECANICO"), service("execution-service")).getBody().allowed());
    }

    @Test
    void clientRequiresCurrentOwnershipForDecisionAndCheckout() {
        var order = mock(OrdemServico.class);
        var customer = UUID.randomUUID();
        when(order.getClienteId()).thenReturn(customer);
        when(orders.buscar(osId)).thenReturn(order);
        when(clients.verificar(customer)).thenReturn(true, false);
        assertTrue(controller.financial(new FinancialRequest(userId, osId, FinancialAction.DECIDE, UUID.randomUUID()), user("CLIENTE"), service("billing-service")).getBody().allowed());
        assertFalse(controller.financial(new FinancialRequest(userId, osId, FinancialAction.CHECKOUT, UUID.randomUUID()), user("CLIENTE"), service("billing-service")).getBody().allowed());
        verifyNoInteractions(grants);
    }

    @Test
    void managerCanReadGrantedBranchButCannotDecideForClient() {
        var order = mock(OrdemServico.class);
        var branch = UUID.randomUUID();
        when(order.getFilialId()).thenReturn(branch);
        when(orders.buscar(osId)).thenReturn(order);
        when(grants.branch(userId, branch, Capability.OS_READ)).thenReturn(true);
        assertTrue(controller.financial(new FinancialRequest(userId, osId, FinancialAction.READ, UUID.randomUUID()), user("GERENTE"), service("billing-service")).getBody().allowed());
        assertFalse(controller.financial(new FinancialRequest(userId, osId, FinancialAction.DECIDE, UUID.randomUUID()), user("GERENTE"), service("billing-service")).getBody().allowed());
        verifyNoInteractions(clients);
    }
}
