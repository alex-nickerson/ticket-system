package com.alexnickerson.ticketsystem.security;

import com.alexnickerson.ticketsystem.common.BadRequestException;
import com.alexnickerson.ticketsystem.user.User;
import com.alexnickerson.ticketsystem.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands out a signed token for one of the seeded development users, so the API
 * can be exercised offline without Microsoft Entra ID.
 *
 * <p>This is not a login: there is no password, and anyone who can reach the
 * endpoint can become any user. It exists only under the dev profile, which is
 * never active in a deployed environment.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@Tag(name = "Dev auth", description = "Development-only token issuing; absent outside the dev profile")
public class DevTokenController {

	private final UserRepository users;
	private final JwtEncoder jwtEncoder;
	private final DevAuthProperties properties;
	private final Clock clock;

	public DevTokenController(
			UserRepository users, JwtEncoder jwtEncoder, DevAuthProperties properties, Clock clock) {
		this.users = users;
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	@PostMapping("/token")
	@Operation(
			summary = "Mint a development token",
			description = "Returns a bearer token for a seeded user. Development profile only.")
	public DevTokenResponse mint(@Valid @RequestBody DevTokenRequest request) {
		User user = users.findById(request.userId())
				.orElseThrow(() -> new BadRequestException(
						"No user exists with id %d.".formatted(request.userId())));

		if (!user.isActive()) {
			throw new BadRequestException("User %d is not active.".formatted(request.userId()));
		}

		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.tokenValidity());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(user.getEmail())
				// The local user id. Milestone 7 will also carry Entra's object id and
				// resolve users by that instead.
				.claim("uid", user.getId())
				.claim("name", user.getDisplayName())
				// A list, matching the shape of Entra ID's app-roles claim, even though
				// a user holds exactly one role in this application.
				.claim("roles", List.of(user.getRole().name()))
				.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

		return new DevTokenResponse(
				token,
				properties.tokenValidity().toSeconds(),
				user.getId(),
				user.getDisplayName(),
				user.getRole().name());
	}

	/** Body of POST /api/dev/token. */
	public record DevTokenRequest(@NotNull Long userId) {}

	/** A minted development token and who it belongs to. */
	public record DevTokenResponse(
			String token, long expiresInSeconds, Long userId, String displayName, String role) {}
}
