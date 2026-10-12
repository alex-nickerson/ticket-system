package com.alexnickerson.ticketsystem.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * The decoder used whenever no profile has configured a real token issuer.
 *
 * <p>A resource server needs a {@code JwtDecoder} bean or the application will
 * not start, so this exists to make "no profile" mean "every request is
 * rejected" rather than "the application fails to boot" or, worse, "security is
 * quietly switched off".
 *
 * <p>Milestone 7 adds a prod-profile decoder backed by Entra ID's public keys,
 * at which point this remains only as the fallback for a misconfigured
 * deployment.
 */
@Component
@Profile("!dev")
public class LockedDownJwtDecoder implements JwtDecoder {

	@Override
	public Jwt decode(String token) throws JwtException {
		throw new JwtException(
				"No token issuer is configured for the active profile. Start the "
						+ "application with the dev profile for local tokens, or configure the "
						+ "prod issuer.");
	}
}
