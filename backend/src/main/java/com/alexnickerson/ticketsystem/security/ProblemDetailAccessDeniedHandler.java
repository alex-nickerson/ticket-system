package com.alexnickerson.ticketsystem.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Returns a 403 as an RFC 7807 problem detail.
 *
 * <p>A 403 here means "you are who you say you are, and you still may not do
 * this". Cases where even the existence of the resource should be hidden return
 * 404 instead, which is decided in the service layer rather than here.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException)
			throws IOException {

		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		problem.setType(URI.create("https://ticket-system.example/problems/forbidden"));
		problem.setTitle("Not permitted");
		problem.setDetail("Your role does not allow this action.");
		problem.setInstance(URI.create(request.getRequestURI()));

		response.setStatus(HttpStatus.FORBIDDEN.value());
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
