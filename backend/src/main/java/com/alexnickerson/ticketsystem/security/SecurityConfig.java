package com.alexnickerson.ticketsystem.security;

import com.alexnickerson.ticketsystem.user.UserRole;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The API is a stateless OAuth2 resource server: it validates a bearer token on
 * every request and keeps no session.
 *
 * <p>Where the token comes from is a profile concern, not a concern of this
 * class. The dev profile mints tokens locally ({@link DevJwtConfiguration}); any
 * other profile currently gets {@link LockedDownJwtDecoder}, which rejects
 * everything, and milestone 7 replaces that with Entra ID's issuer.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

	private final CorsProperties corsProperties;

	public SecurityConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Bean
	SecurityFilterChain filterChain(
			HttpSecurity http,
			ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler)
			throws Exception {

		http
				.cors(Customizer.withDefaults())
				// There is no cookie or session to protect, and the token has to be
				// attached by JavaScript, so a cross-site form post cannot
				// authenticate. CSRF protection would add nothing here.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						// Documentation and health are open: they expose no ticket data.
						.requestMatchers(
								"/actuator/health", "/actuator/health/**", "/actuator/info",
								"/v3/api-docs", "/v3/api-docs/**",
								"/swagger-ui.html", "/swagger-ui/**")
						.permitAll()
						// The dev token endpoint has to be reachable without a token. The
						// controller only exists under the dev profile, so in any other
						// profile this path is simply a 404.
						.requestMatchers("/api/dev/**").permitAll()
						// CORS preflight carries no credentials by definition.
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						// Listing agents is for assigning work, which requesters cannot do.
						.requestMatchers("/api/users/**")
						.hasAnyRole(UserRole.AGENT.name(), UserRole.ADMIN.name())
						.requestMatchers("/api/**").authenticated()
						// Anything not named above is denied rather than quietly open.
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler));

		return http.build();
	}

	/**
	 * Maps the token's {@code roles} claim onto Spring Security authorities.
	 *
	 * <p>The claim is called {@code roles} and holds bare role names because that
	 * is the shape Entra ID uses for app roles. Matching it now means milestone 7
	 * changes the token issuer without changing how roles are read.
	 */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		// hasRole("AGENT") looks for the authority ROLE_AGENT.
		authorities.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		// Explicit origins, never "*": a wildcard cannot be combined with
		// credentials and would let any site call the API from a browser.
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
