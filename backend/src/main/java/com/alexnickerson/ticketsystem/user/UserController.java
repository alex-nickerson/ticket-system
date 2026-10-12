package com.alexnickerson.ticketsystem.user;

import com.alexnickerson.ticketsystem.ticket.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the caller is, and who the agents are.
 *
 * <p>The agent list is restricted to agents and admins by the filter chain: it
 * exists so work can be assigned, which requesters cannot do, and a staff
 * directory is not something to hand out to everyone.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Users", description = "The current user, and agents available for assignment")
public class UserController {

	private final UserRepository users;
	private final ActingUserProvider actingUserProvider;

	public UserController(UserRepository users, ActingUserProvider actingUserProvider) {
		this.users = users;
		this.actingUserProvider = actingUserProvider;
	}

	@GetMapping("/me")
	@Operation(
			summary = "The authenticated user",
			description = "Resolved from the bearer token. The role is read from the database rather "
					+ "than taken from the token, so a role change takes effect immediately.")
	public CurrentUserResponse me() {
		User user = actingUserProvider.current();
		return new CurrentUserResponse(
				user.getId(), user.getDisplayName(), user.getEmail(), user.getRole(), user.isActive());
	}

	@GetMapping("/users")
	@Operation(summary = "List users by role, for assignment dropdowns")
	public List<UserSummary> byRole(@RequestParam UserRole role) {
		return users.findByRoleOrderByDisplayNameAsc(role).stream().map(UserSummary::from).toList();
	}

	/** The caller's own details, including the role the UI uses to hide controls. */
	public record CurrentUserResponse(
			Long id, String displayName, String email, UserRole role, boolean active) {}
}
