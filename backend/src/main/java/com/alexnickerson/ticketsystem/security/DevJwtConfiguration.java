package com.alexnickerson.ticketsystem.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Local token issuing and validation for the dev profile.
 *
 * <p>Tokens are signed with a symmetric HMAC key, which means the application is
 * both issuer and audience. That is fine for development and wrong for
 * production: a shared secret cannot be verified by anyone who is not also able
 * to mint tokens. Entra ID signs with a private key and publishes the public
 * half, which is why milestone 7 swaps this whole configuration out rather than
 * extending it.
 */
@Configuration
@Profile("dev")
@EnableConfigurationProperties(DevAuthProperties.class)
public class DevJwtConfiguration {

	private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

	private final SecretKeySpec signingKey;

	public DevJwtConfiguration(DevAuthProperties properties) {
		byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			// HS256 requires a key at least as long as its output. Failing here with
			// a clear message beats a confusing error on the first token.
			throw new IllegalStateException(
					"app.dev-auth.secret must be at least 32 bytes for " + ALGORITHM.getName());
		}
		this.signingKey = new SecretKeySpec(keyBytes, ALGORITHM.getName());
	}

	@Bean
	JwtEncoder devJwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
	}

	@Bean
	JwtDecoder devJwtDecoder() {
		return NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(ALGORITHM).build();
	}
}
