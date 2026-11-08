package com.oficina.os.application.authorization;

import com.oficina.os.application.port.in.*;
import com.oficina.os.application.port.out.*;
import com.oficina.os.application.usecase.*;
import com.oficina.os.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthorizedBusinessUseCase {
    private final CurrentActor current;
    private final ResourceAuthorization authorization;
    private final OperationalAccessPort grants;
    private final AuthorizedOrderQuery query;
    private final ClienteUseCase clients;
    private final VeiculoUseCase vehicles;
    private final FilialUseCase branches;
    private final OrdemServicoUseCase orders;

    public AuthorizedBusinessUseCase(CurrentActor current, ResourceAuthorization authorization, OperationalAccessPort grants,
            AuthorizedOrderQuery query, ClienteUseCase clients, VeiculoUseCase vehicles, FilialUseCase branches, OrdemServicoUseCase orders) {
        this.current = current;
        this.authorization = authorization;
        this.grants = grants;
        this.query = query;
        this.clients = clients;
        this.vehicles = vehicles;
        this.branches = branches;
        this.orders = orders;
    }

    @Transactional
    public Cliente criarCliente(ClienteCommand command, UUID filialId) {
        authorization.branch(current.get(), filialId, Capability.CLIENT_CREATE, "ATENDENTE");
        branches.buscar(filialId);
        var client = clients.criar(command);
        grants.registerClient(client.getId(), filialId);
        return client;
    }

    public Cliente buscarCliente(UUID id) {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE");
        if (!grants.clients(actor.usuarioId()).contains(id)) { throw new AuthorizationDenied(); }
        return clients.buscar(id);
    }

    public List<Cliente> listarClientes() {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE");
        return grants.clients(actor.usuarioId()).stream().map(clients::buscar).toList();
    }

    @Transactional
    public Veiculo criarVeiculo(VeiculoCommand command, UUID filialId) {
        authorization.branch(current.get(), filialId, Capability.VEHICLE_CREATE, "ATENDENTE");
        if (!grants.clientInBranch(command.clienteId(), filialId)) { throw new AuthorizationDenied(); }
        var vehicle = vehicles.criar(command);
        grants.registerVehicle(vehicle.getId(), filialId);
        return vehicle;
    }

    public Veiculo buscarVeiculo(UUID id) {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE", "CLIENTE");
        var vehicle = vehicles.buscar(id);
        if (actor.has("ATENDENTE")) {
            if (!grants.vehicles(actor.usuarioId(), vehicle.getClienteId()).contains(id)) { throw new AuthorizationDenied(); }
        } else { authorization.client(actor, vehicle.getClienteId()); }
        return vehicle;
    }

    public List<Veiculo> listarVeiculos(UUID clienteId) {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE", "CLIENTE");
        if (actor.has("ATENDENTE")) {
            return grants.vehicles(actor.usuarioId(), clienteId).stream().map(vehicles::buscar).toList();
        }
        authorization.client(actor, clienteId);
        return vehicles.listarPorCliente(clienteId);
    }

    @Transactional
    public Filial criarFilial(FilialCommand command) {
        authorization.administrative(current.get(), Capability.BRANCH_CREATE);
        return branches.criar(command);
    }

    public Filial buscarFilial(UUID id) {
        authorization.branch(current.get(), id, Capability.BRANCH_READ, "ATENDENTE", "GERENTE");
        return branches.buscar(id);
    }

    public List<Filial> listarFiliais() {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE", "GERENTE");
        return grants.branches(actor.usuarioId(), Capability.BRANCH_READ).stream().map(branches::buscar).toList();
    }

    @Transactional
    public OrdemServico abrir(AbrirOrdemServicoCommand command) {
        authorization.branch(current.get(), command.filialId(), Capability.OS_OPEN, "ATENDENTE");
        if (!grants.clientInBranch(command.clienteId(), command.filialId())
                || !grants.vehicleInBranch(command.veiculoId(), command.filialId())) { throw new AuthorizationDenied(); }
        return orders.abrir(command);
    }

    public OrdemServico buscar(UUID id) {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE", "GERENTE", "CLIENTE");
        var order = orders.buscar(id);
        authorization.order(actor, order);
        return order;
    }

    public PageResult<OrdemServico> pesquisar(OrdemServicoSearchCriteria criteria) {
        var actor = current.get();
        authorization.role(actor, "ATENDENTE", "GERENTE", "CLIENTE");
        if (!actor.has("ATENDENTE") && !actor.has("GERENTE")) {
            if (criteria.clienteId() == null) { throw new AuthorizationDenied(); }
            authorization.client(actor, criteria.clienteId());
            return orders.pesquisar(criteria);
        }
        return query.search(criteria, actor);
    }

    public List<HistoricoStatus> historico(UUID id) {
        buscar(id);
        return orders.historico(id);
    }

    public SagaInstance saga(UUID id) {
        var actor = current.get();
        authorization.role(actor, "GERENTE");
        var order = orders.buscar(id);
        authorization.branch(actor, order.getFilialId(), Capability.SAGA_INSPECT, "GERENTE");
        return orders.saga(id);
    }

    public OrdemServico operacional(UUID id) {
        authorization.mechanic(current.get(), id, Capability.OPERATIONAL_READ);
        return orders.buscar(id);
    }

    public Veiculo veiculoOperacional(UUID id) {
        authorization.mechanic(current.get(), id, Capability.VEHICLE_OPERATIONAL_READ);
        return vehicles.buscar(orders.buscar(id).getVeiculoId());
    }
}
