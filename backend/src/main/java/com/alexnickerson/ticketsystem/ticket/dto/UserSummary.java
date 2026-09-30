package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.user.User;

/** The slice of a user that ticket responses expose. */
public record UserSummary(Long id, String displayName, String email) {

	public static UserSummary from(User user) {
		return user == null ? null : new UserSummary(user.getId(), user.getDisplayName(), user.getEmail());
	}
}
