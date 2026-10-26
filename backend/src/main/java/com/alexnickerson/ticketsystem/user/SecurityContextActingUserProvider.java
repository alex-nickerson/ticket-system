package com.alexnickerson.ticketsystem.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the acting user from the validated bearer token.
 *
 * <p>This replaced {@code HeaderActingUserProvider} — the milestone 1 placeholder
 * that trusted an {@code X-Acting-User} header — and it was the only file that
 * had to change, which is why the {@link ActingUserProvider} interface existed in
 * the first place.
 *
 * <p>The user is looked up by the token's {@code uid} claim rather than trusted
 * wholesale: a token says who the caller is, but the role and active flag that
 * authorization depends on are read from the database, so deactivating someone
 * takes effect immediately instead of when their token expires.
 */
@Component
public class SecurityContextActingUserProvider implements ActingUserProvider {

	static final String USER_ID_CLAIM = "uid";

	private final UserRepository users;

	public SecurityContextActingUserProvider(UserRepository users) {
		this.users = users;
	}

	@Override
	public User current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
			// The filter chain requires authentication on every /api route, so this
			// means a bug rather than an unauthenticated caller.
			throw new ActingUserException("The request is not authenticated with a bearer token.");
		}

		Jwt jwt = jwtAuthentication.getToken();
		Object claim = jwt.getClaim(USER_ID_CLAIM);
		if (claim == null) {
			throw new ActingUserException(
					"The token carries no %s claim identifying the user.".formatted(USER_ID_CLAIM));
		}

		long userId;
		try {
			userId = Long.parseLong(claim.toString());
		} catch (NumberFormatException ex) {
			throw new ActingUserException("The %s claim is not a user id.".formatted(USER_ID_CLAIM));
		}

		User user = users.findById(userId)
				.orElseThrow(() -> new ActingUserException(
						"The token refers to user %d, who no longer exists.".formatted(userId)));

		if (!user.isActive()) {
			throw new ActingUserException("User %d is no longer active.".formatted(userId));
		}
		return user;
	}
}
