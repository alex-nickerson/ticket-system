package com.alexnickerson.ticketsystem.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alexnickerson.ticketsystem.TestcontainersConfiguration;
import com.alexnickerson.ticketsystem.support.ForeignTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the decision that dev authentication is opt-in.
 *
 * <p>No profile is active here, which is what a deployment would look like if
 * somebody forgot to set one. The application must still start, must serve no
 * ticket data to anybody, and must not expose the dev token endpoint — the
 * failure mode is "nothing works", never "anyone can be an admin".
 *
 * <p>Note the two different failures: a request with no credentials is a 401,
 * while a request that presents a token the server cannot possibly verify is a
 * server error, because the fault is the server's.
 *
 * <p>Without this test the guarantee would rest on {@link LockedDownJwtDecoder}
 * being wired correctly, which is exactly the sort of thing that quietly breaks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LockedDownWithoutDevProfileTest {

	@Autowired private MockMvc mockMvc;

	@Test
	void theApplicationStartsRatherThanFailingToBoot() {
		// Reaching this method at all means the context loaded without a real token
		// issuer configured, which is the point of LockedDownJwtDecoder.
	}

	@Test
	void theDevTokenEndpointDoesNotExist() throws Exception {
		mockMvc.perform(post("/api/dev/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"userId\": 1}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void ticketsAreUnreachableWithoutAToken() throws Exception {
		mockMvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
	}

	@Test
	void presentingATokenFailsAsAServerMisconfigurationRatherThanA401() {
		// Even a properly signed token cannot be verified, because this profile has
		// no signing key at all.
		String token = ForeignTokens.signedWithAnUnknownKey();

		/*
		 * This is a 500, not a 401, and that is Spring Security's deliberate
		 * choice rather than an accident: a token it cannot check because the
		 * server has no issuer configured is a server fault, so it is reported as
		 * one instead of being disguised as "your credentials are wrong".
		 *
		 * Operationally that is what you want. A misconfigured deployment fails
		 * loudly and unmistakably, rather than sending every user to debug their
		 * own token. The security guarantee is unaffected: no ticket data is
		 * served either way.
		 */
		assertThatThrownBy(() -> mockMvc.perform(
						get("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)))
				.hasMessageContaining("No token issuer is configured");
	}

	@Test
	void healthStaysOpenSoTheDeploymentCanBeMonitored() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
