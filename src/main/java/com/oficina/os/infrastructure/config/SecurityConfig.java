package com.oficina.os.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.oficina.os.infrastructure.security.AuthJwtDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import java.time.Clock;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	@Bean
	JwtDecoder jwtDecoder(@Value("${oficina.security.jwt.issuer}") String issuer,
			@Value("${oficina.security.jwt.audience}") String audience,
			@Value("${oficina.security.jwt.jwks-uri}") String jwksUri) {
		return new AuthJwtDecoder(issuer, audience, jwksUri, Clock.systemUTC());
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		var authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return http.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> unauthorized(response))
						.accessDeniedHandler((request, response, exception) -> forbidden(response)))
				.authorizeHttpRequests(rules -> rules
						.requestMatchers(HttpMethod.POST, "/internal/technical-authorization", "/internal/financial-authorization").authenticated()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
						.requestMatchers(HttpMethod.PUT, "/api/v1/clientes/*/filiais/*", "/api/v1/veiculos/*/filiais/*").hasRole("ATENDENTE")
						.requestMatchers(HttpMethod.POST, "/api/v1/clientes", "/api/v1/veiculos", "/api/v1/ordens-servico").hasRole("ATENDENTE")
						.requestMatchers(HttpMethod.POST, "/api/v1/filiais").hasRole("GERENTE")
						.requestMatchers(HttpMethod.GET, "/api/v1/ordens-servico/*/saga").hasRole("GERENTE")
						.requestMatchers(HttpMethod.GET, "/api/v1/ordens-servico/*/operacional", "/api/v1/ordens-servico/*/veiculo-operacional").hasRole("MECANICO")
						.requestMatchers(HttpMethod.GET, "/api/v1/clientes", "/api/v1/clientes/*").hasRole("ATENDENTE")
						.requestMatchers(HttpMethod.GET, "/api/v1/veiculos", "/api/v1/veiculos/*").hasAnyRole("CLIENTE", "ATENDENTE")
						.requestMatchers(HttpMethod.GET, "/api/v1/filiais", "/api/v1/filiais/*").hasAnyRole("ATENDENTE", "GERENTE")
						.requestMatchers(HttpMethod.GET, "/api/v1/ordens-servico", "/api/v1/ordens-servico/*", "/api/v1/ordens-servico/*/historico").hasAnyRole("CLIENTE", "ATENDENTE", "GERENTE")
						.anyRequest().denyAll())
				.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
						.authenticationEntryPoint((request, response, exception) -> unauthorized(response))
						.accessDeniedHandler((request, response, exception) -> forbidden(response)))
				.build();
	}

	private static void unauthorized(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
		response.setStatus(401);
		response.setHeader("WWW-Authenticate", "Bearer");
		response.setHeader("Cache-Control", "no-store");
		response.setContentType("application/problem+json");
		response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"detail\":\"Authentication required or invalid\"}");
	}

	private static void forbidden(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
		response.setStatus(403);
		response.setHeader("Cache-Control", "no-store");
		response.setContentType("application/problem+json");
		response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"Access denied\"}");
	}
}
