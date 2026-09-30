package com.alexnickerson.ticketsystem.category;

import com.alexnickerson.ticketsystem.ticket.dto.CategorySummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only list of selectable categories, so a create form has something to
 * populate its dropdown from. Administering categories (create, rename,
 * deactivate) needs the Admin role and therefore waits for milestone 2.
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Reference data for raising tickets")
public class CategoryController {

	private final CategoryRepository categories;

	public CategoryController(CategoryRepository categories) {
		this.categories = categories;
	}

	@GetMapping
	@Operation(summary = "List active categories, alphabetically")
	public List<CategorySummary> list() {
		return categories.findByActiveTrueOrderByNameAsc().stream()
				.map(CategorySummary::from)
				.toList();
	}
}
