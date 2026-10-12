package com.alexnickerson.ticketsystem.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.alexnickerson.ticketsystem.category.Category;
import com.alexnickerson.ticketsystem.category.CategoryRepository;
import com.alexnickerson.ticketsystem.user.User;
import com.alexnickerson.ticketsystem.user.UserRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Mints real bearer tokens for the seeded development users by calling the
 * application's own {@code POST /api/dev/token} endpoint.
 *
 * <p>Tokens are deliberately obtained the way a developer would, rather than
 * forged with a test-only signing helper. That means every test using this also
 * exercises the token endpoint, the signature, the decoder and the roles claim —
 * if any of those break the tests fail, instead of passing against a parallel
 * code path that production never runs.
 *
 * <p>Not a Spring bean: tests construct it in their setup method from components
 * they already have injected.
 */
public final class DevTokens {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserRepository users;
	private final CategoryRepository categories;

	public DevTokens(
			MockMvc mockMvc,
			ObjectMapper objectMapper,
			UserRepository users,
			CategoryRepository categories) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.users = users;
		this.categories = categories;
	}

	public String bearerForRequester() throws Exception {
		return bearerFor("requester@example.test");
	}

	public String bearerForAgent() throws Exception {
		return bearerFor("agent@example.test");
	}

	public String bearerForAdmin() throws Exception {
		return bearerFor("admin@example.test");
	}

	/** An {@code Authorization} header value for the seeded user with this email. */
	public String bearerFor(String email) throws Exception {
		return "Bearer " + tokenFor(userId(email));
	}

	public String tokenFor(long userId) throws Exception {
		String response = mockMvc.perform(post("/api/dev/token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"userId\": %d}".formatted(userId)))
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).get("token").asText();
	}

	public long userId(String email) {
		return users.findByEmail(email)
				.map(User::getId)
				.orElseThrow(() -> new IllegalStateException("Missing seeded user " + email));
	}

	public long categoryId(String name) {
		return categories.findByActiveTrueOrderByNameAsc().stream()
				.filter(category -> category.getName().equals(name))
				.map(Category::getId)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing seeded category " + name));
	}
}
