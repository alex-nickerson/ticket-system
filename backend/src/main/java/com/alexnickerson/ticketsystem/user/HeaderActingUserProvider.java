package com.alexnickerson.ticketsystem.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Development-only {@link ActingUserProvider}: the caller states who they are in
 * an {@code X-Acting-User} header holding a user id.
 *
 * <p>This is obviously not authentication — anyone can claim to be anyone. It is
 * a deliberate placeholder so that milestone 1 can build and test the ticket API
 * before Spring Security arrives in milestone 2, and it is confined to this one
 * class so that replacing it is a single-file change.
 */
@Component
public class HeaderActingUserProvider implements ActingUserProvider {

	public static final String HEADER = "X-Acting-User";

	private final HttpServletRequest request;
	private final UserRepository users;

	/**
	 * Spring injects a scoped proxy for {@code HttpServletRequest}, so this
	 * singleton still sees the request currently being handled.
	 */
	public HeaderActingUserProvider(HttpServletRequest request, UserRepository users) {
		this.request = request;
		this.users = users;
	}

	@Override
	public User current() {
		String raw = request.getHeader(HEADER);
		if (raw == null || raw.isBlank()) {
			throw new ActingUserException(
					"The %s header is required until authentication is added.".formatted(HEADER));
		}

		long id;
		try {
			id = Long.parseLong(raw.trim());
		} catch (NumberFormatException ex) {
			throw new ActingUserException("The %s header must be a numeric user id.".formatted(HEADER));
		}

		User user = users.findById(id)
				.orElseThrow(() -> new ActingUserException("No user exists with id %d.".formatted(id)));

		if (!user.isActive()) {
			throw new ActingUserException("User %d is not active.".formatted(id));
		}
		return user;
	}
}
