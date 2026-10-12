package com.oficina.os.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JacksonConfig {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}
}
