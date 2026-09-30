package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.category.Category;

/** The slice of a category that ticket responses expose. */
public record CategorySummary(Long id, String name) {

	public static CategorySummary from(Category category) {
		return category == null ? null : new CategorySummary(category.getId(), category.getName());
	}
}
