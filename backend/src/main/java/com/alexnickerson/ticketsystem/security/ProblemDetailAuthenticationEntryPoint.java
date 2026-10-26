package com.alexnickerson.ticketsystem.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Returns a 401 as an RFC 7807 problem detail.
 *
 * <p>Spring Security's default writes an empty body, which would make
 * authentication the only error in the API that does not follow the same shape
 * as everything else.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authenticationException)
			throws IOException {

		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
		problem.setType(URI.create("https://ticket-system.example/problems/unauthenticated"));
		problem.setTitle("Not authenticated");
		// Deliberately generic: why a token was rejected (expired, wrong issuer,
		// bad signature) is useful to an attacker and not to a legitimate client.
		problem.setDetail("A valid bearer token is required.");
		problem.setInstance(URI.create(request.getRequestURI()));

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		// Without this the servlet default (ISO-8859-1) is used and any non-ASCII
		// text in the problem detail is mangled. The problem details produced by
		// the @RestControllerAdvice go through Spring's message converters, which
		// already use UTF-8; these two handlers write the response themselves and
		// so have to say so.
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), problem);
	}
}
