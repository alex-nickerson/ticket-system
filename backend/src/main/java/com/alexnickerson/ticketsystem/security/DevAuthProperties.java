package com.alexnickerson.ticketsystem.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the locally minted development tokens. Bound only under the dev
 * profile.
 *
 * @param secret HMAC signing key; a development placeholder, not a real secret
 * @param issuer value placed in the token's {@code iss} claim
 * @param tokenValidity how long a minted token lasts
 */
@ConfigurationProperties(prefix = "app.dev-auth")
public record DevAuthProperties(String secret, String issuer, Duration tokenValidity) {}
