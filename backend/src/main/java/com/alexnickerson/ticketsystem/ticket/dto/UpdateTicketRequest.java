package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.ticket.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Body of PATCH /api/tickets/{id}. A null field means "leave this alone", which
 * is what makes the request a patch rather than a replacement.
 */
@Schema(description = "Only the fields present are changed")
public record UpdateTicketRequest(
		@Size(max = 200) String title,
		@Size(max = 10_000) String description,
		Long categoryId,
		Priority priority) {

	@AssertTrue(message = "at least one field must be provided")
	public boolean isAtLeastOneFieldPresent() {
		return title != null || description != null || categoryId != null || priority != null;
	}

	/**
	 * {@code @Size} accepts a blank string, and {@code @NotBlank} would reject a
	 * field that is simply absent, so blankness is checked separately.
	 */
	@AssertTrue(message = "provided text fields must not be blank")
	public boolean isProvidedTextNotBlank() {
		return (title == null || !title.isBlank()) && (description == null || !description.isBlank());
	}
}
