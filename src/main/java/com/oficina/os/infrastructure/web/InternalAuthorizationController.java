package com.oficina.os.infrastructure.web;

import com.oficina.os.application.authorization.*;
import com.oficina.os.application.usecase.OrdemServicoUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class InternalAuthorizationController {
    private static final Set<Capability> TECHNICAL = Set.of(Capability.DIAGNOSTIC_READ, Capability.DIAGNOSTIC_WRITE,
            Capability.DIAGNOSTIC_COMPLETE, Capability.REPAIR_READ, Capability.REPAIR_WRITE,
            Capability.REPAIR_COMPLETE, Capability.HISTORY_READ);
    private final OrdemServicoUseCase orders;
    private final ResourceAuthorization authorization;

    public InternalAuthorizationController(OrdemServicoUseCase orders, ResourceAuthorization authorization) {
        this.orders = orders;
        this.authorization = authorization;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Integer>> rejected(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("status", exception.getStatusCode().value()));
    }

    @PostMapping("/internal/technical-authorization")
    public ResponseEntity<TechnicalDecision> technical(@RequestBody TechnicalRequest body,
            JwtAuthenticationToken user, HttpServletRequest request) {
        service(request, "execution-service");
        var actor = actor(user);
        if (!actor.usuarioId().equals(body.usuarioId()) || body.osId() == null || body.correlationId() == null
                || body.capability() == null || !TECHNICAL.contains(body.capability())) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
        orders.buscar(body.osId());
        boolean allowed;
        try {
            authorization.mechanic(actor, body.osId(), body.capability());
            allowed = true;
        } catch (AuthorizationDenied exception) { allowed = false; }
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new TechnicalDecision(actor.usuarioId(), body.osId(), body.capability(), body.correlationId(), allowed));
    }

    @PostMapping("/internal/financial-authorization")
    public ResponseEntity<FinancialDecision> financial(@RequestBody FinancialRequest body,
            JwtAuthenticationToken user, HttpServletRequest request) {
        service(request, "billing-service");
        var actor = actor(user);
        if (!actor.usuarioId().equals(body.usuarioId()) || body.osId() == null || body.correlationId() == null
                || body.action() == null) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
        var order = orders.buscar(body.osId());
        boolean allowed;
        try {
            if (actor.has("CLIENTE")) {
                authorization.client(actor, order.getClienteId());
            } else if (body.action() == FinancialAction.READ) {
                authorization.branch(actor, order.getFilialId(), Capability.OS_READ, "ATENDENTE", "GERENTE");
            } else { throw new AuthorizationDenied(); }
            allowed = true;
        } catch (AuthorizationDenied exception) { allowed = false; }
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new FinancialDecision(actor.usuarioId(), body.osId(), body.action(), body.correlationId(), allowed));
    }

    private Actor actor(JwtAuthenticationToken user) {
        return new Actor(UUID.fromString(user.getToken().getSubject()), user.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "")).collect(Collectors.toSet()));
    }

    private void service(HttpServletRequest request, String expected) {
        var certificates = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!request.isSecure() || certificates == null || certificates.length == 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (certificates[0].getBasicConstraints() != -1
                || !certificates[0].getSubjectX500Principal().getName().equals("CN=" + expected)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    public record TechnicalRequest(UUID usuarioId, UUID osId, Capability capability, UUID correlationId) {}
    public record TechnicalDecision(UUID usuarioId, UUID osId, Capability capability, UUID correlationId, boolean allowed) {}
    public enum FinancialAction { READ, DECIDE, CHECKOUT }
    public record FinancialRequest(UUID usuarioId, UUID osId, FinancialAction action, UUID correlationId) {}
    public record FinancialDecision(UUID usuarioId, UUID osId, FinancialAction action, UUID correlationId, boolean allowed) {}
}
