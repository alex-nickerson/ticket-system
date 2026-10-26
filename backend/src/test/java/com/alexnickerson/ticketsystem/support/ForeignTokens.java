package com.alexnickerson.ticketsystem.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Builds a well-formed JWT that the application cannot possibly trust, because
 * it is signed with a key generated here and never shared with anything.
 *
 * <p>Used to assert that a forged token is rejected. The token is minted at run
 * time rather than pasted in as a string for two reasons.
 *
 * <p>The first is that it makes the test honest. A hand-written string with a
 * nonsense signature proves only that garbage is rejected; a properly signed
 * token with the wrong key proves the signature is actually verified, which is
 * the thing worth testing. The claims deliberately assert the ADMIN role, so the
 * test also shows that what a token *claims* counts for nothing when it cannot be
 * verified.
 *
 * <p>The second is that a base64 JWT segment in source code is indistinguishable
 * from a leaked API key. The {@code gitleaks} scan in CI flagged exactly that and
 * failed the build, and it was right to: a scanner that cannot tell a fixture
 * from a credential is better left strict than taught to ignore things. Nothing
 * high-entropy is committed now, so there is no allowlist to maintain and no
 * exception for a reviewer to take on trust.
 */
public final class ForeignTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private ForeignTokens() {
		// Utility class.
	}

	/** A valid HS256 token signed with a key this application has never seen. */
	public static String signedWithAnUnknownKey() {
		byte[] keyBytes = new byte[32];
		RANDOM.nextBytes(keyBytes);
		SecretKeySpec unknownKey = new SecretKeySpec(keyBytes, MacAlgorithm.HS256.getName());

		JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(unknownKey));

		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("an-issuer-this-application-does-not-trust")
				.subject("forged@example.test")
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofHours(1)))
				.claim("uid", 3)
				// Claiming the highest role on purpose: it must still be worthless.
				.claim("roles", List.of("ADMIN"))
				.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
