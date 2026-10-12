package com.alexnickerson.ticketsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

	private static final String BEARER_SCHEME = "bearerAuth";

	/**
	 * Declares the bearer-token scheme so Swagger UI shows an "Authorize" button.
	 * Without it every call from the documentation page would come back 401 and
	 * the docs would be useless for trying the API out.
	 */
	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Help desk ticketing API")
						.version("v1")
						.description("""
								Authenticate with a bearer token. Under the dev profile, mint one \
								with POST /api/dev/token for a seeded user, then paste it into \
								Authorize."""))
				.components(new Components().addSecuritySchemes(
						BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
