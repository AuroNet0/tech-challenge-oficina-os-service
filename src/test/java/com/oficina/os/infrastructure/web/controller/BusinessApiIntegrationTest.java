package com.oficina.os.infrastructure.web.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BusinessApiIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("oficina_os_api_test")
			.withUsername("oficina_os")
			.withPassword("oficina_os");

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void businessRoutesRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/clientes"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createsAndReadsClienteFilialVeiculoAndOrdemServicoWithHistory() throws Exception {
		Response cliente = createCliente("529.982.247-25");
		Response filial = createFilial("SP-CENTRO-A");
		Response veiculo = createVeiculo(cliente.id(), "ABC-1234");

		String osBody = """
				{
				  "clienteId": "%s",
				  "veiculoId": "%s",
				  "filialId": "%s",
				  "observacoes": "Barulho no motor"
				}
				""".formatted(cliente.id(), veiculo.id(), filial.id());

		String osResponse = mockMvc.perform(post("/api/v1/ordens-servico")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(osBody))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("RECEBIDA"))
				.andExpect(jsonPath("$.observacoes").value("Barulho no motor"))
				.andReturn().getResponse().getContentAsString();
		Response ordemServico = parse(osResponse);

		mockMvc.perform(get("/api/v1/ordens-servico/{id}", ordemServico.id()).with(user("api")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clienteId").value(cliente.id()))
				.andExpect(jsonPath("$.veiculoId").value(veiculo.id()))
				.andExpect(jsonPath("$.filialId").value(filial.id()));

		mockMvc.perform(get("/api/v1/ordens-servico/{id}/historico", ordemServico.id()).with(user("api")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].statusAnterior").doesNotExist())
				.andExpect(jsonPath("$[0].statusNovo").value("RECEBIDA"));

		mockMvc.perform(get("/api/v1/ordens-servico")
						.with(user("api"))
						.param("clienteId", cliente.id())
						.param("veiculoId", veiculo.id())
						.param("filialId", filial.id())
						.param("status", "RECEBIDA")
						.param("page", "0")
						.param("size", "10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(ordemServico.id()))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void returnsBadRequestNotFoundAndConflict() throws Exception {
		mockMvc.perform(post("/api/v1/clientes")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"\",\"cpfCnpj\":\"111\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/clientes/not-a-uuid").with(user("api")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));

		mockMvc.perform(get("/api/v1/clientes/00000000-0000-0000-0000-000000000000").with(user("api")))
				.andExpect(status().isNotFound());

		createCliente("04.252.011/0001-10");
		mockMvc.perform(post("/api/v1/clientes")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nome":"Duplicado","cpfCnpj":"04252011000110","email":null,"telefone":null}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Conflict"));
	}

	@Test
	void rejectsInvalidRelationshipsAndDoesNotExposeStatusTransitionEndpoint() throws Exception {
		Response cliente = createCliente("123.456.789-09");
		Response otherCliente = createCliente("987.654.321-00");
		Response filial = createFilial("SP-CENTRO-B");
		Response veiculo = createVeiculo(otherCliente.id(), "DEF-1234");

		String body = """
				{
				  "clienteId": "%s",
				  "veiculoId": "%s",
				  "filialId": "%s"
				}
				""".formatted(cliente.id(), veiculo.id(), filial.id());

		mockMvc.perform(post("/api/v1/ordens-servico")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/ordens-servico/00000000-0000-0000-0000-000000000000/status")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound());
	}

	private Response createCliente(String cpfCnpj) throws Exception {
		String body = """
				{"nome":"Maria","cpfCnpj":"%s","email":"maria@example.com","telefone":"11999999999"}
				""".formatted(cpfCnpj);
		String response = mockMvc.perform(post("/api/v1/clientes")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Boolean>read(response, "$.ativo")).isTrue();
		return parse(response);
	}

	private Response createFilial(String codigo) throws Exception {
		String body = """
				{"nome":"Centro","codigo":"%s","cidade":"Sao Paulo","uf":"SP"}
				""".formatted(codigo);
		String response = mockMvc.perform(post("/api/v1/filiais")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
		return parse(response);
	}

	private Response createVeiculo(String clienteId, String placa) throws Exception {
		String body = """
				{"clienteId":"%s","placa":"%s","marca":"Fiat","modelo":"Uno","ano":2020}
				""".formatted(clienteId, placa);
		String response = mockMvc.perform(post("/api/v1/veiculos")
						.with(user("api")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
		return parse(response);
	}

	private Response parse(String json) {
		return new Response(JsonPath.read(json, "$.id"));
	}

	private record Response(String id) {
	}
}
