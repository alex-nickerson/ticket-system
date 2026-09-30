package com.alexnickerson.ticketsystem.common;

import com.alexnickerson.ticketsystem.user.ActingUserException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 7807 problem details, so every error in the API has
 * the same machine-readable shape instead of each endpoint inventing its own.
 *
 * <p>Extending {@code ResponseEntityExceptionHandler} means Spring's own
 * exceptions (unreadable body, wrong method, unknown enum value) already come
 * back as problem details; only the cases below need custom handling.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String TYPE_PREFIX = "https://ticket-system.example/problems/";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setType(URI.create(TYPE_PREFIX + "validation-failed"));
		problem.setTitle("Validation failed");
		problem.setDetail("One or more fields are invalid.");

		// Field-level detail lives in an extension member so clients can show the
		// error next to the offending input rather than as one opaque string.
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of(
						"field", error.getField(),
						"message", Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
				.toList();
		problem.setProperty("errors", errors);

		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(BadRequestException.class)
	ProblemDetail handleBadRequest(BadRequestException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setType(URI.create(TYPE_PREFIX + "invalid-request"));
		problem.setTitle("Invalid request");
		problem.setDetail(ex.getMessage());
		return problem;
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setType(URI.create(TYPE_PREFIX + "not-found"));
		problem.setTitle("Not found");
		problem.setDetail(ex.getMessage());
		return problem;
	}

	@ExceptionHandler(ActingUserException.class)
	ProblemDetail handleActingUser(ActingUserException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setType(URI.create(TYPE_PREFIX + "acting-user-invalid"));
		problem.setTitle("Acting user could not be determined");
		problem.setDetail(ex.getMessage());
		return problem;
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problem.setType(URI.create(TYPE_PREFIX + "concurrent-modification"));
		problem.setTitle("Concurrent modification");
		problem.setDetail("This ticket was changed by someone else. Reload it and try again.");
		return problem;
	}
}
