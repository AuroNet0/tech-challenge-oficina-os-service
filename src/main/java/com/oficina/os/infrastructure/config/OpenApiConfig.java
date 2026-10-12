package com.oficina.os.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI osServiceOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Tech Challenge Oficina - OS Service")
						.version("0.0.1")
						.description("Microsservico responsavel por ordens de servico, clientes e veiculos."));
	}
}
