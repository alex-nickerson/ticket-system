package com.alexnickerson.ticketsystem.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins allowed to call the API from a browser.
 *
 * @param allowedOrigins exact origins, never a wildcard
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}
}
