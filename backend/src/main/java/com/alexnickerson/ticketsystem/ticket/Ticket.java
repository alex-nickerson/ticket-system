package com.alexnickerson.ticketsystem.ticket;

import com.alexnickerson.ticketsystem.category.Category;
import com.alexnickerson.ticketsystem.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Human-readable identifier, e.g. INC-000123. Allocated on creation. */
	@Column(nullable = false, length = 20, updatable = false)
	private String number;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private TicketType type;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 10000)
	private String description;

	/*
	 * Associations are LAZY. With open-in-view disabled, anything the response
	 * needs must be touched inside the service's transaction — which is where
	 * entities are mapped to DTOs, so the fetch is deliberate rather than a
	 * surprise during serialization.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 2)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TicketStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_id", nullable = false, updatable = false)
	private User requester;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	// --- Populated by the lifecycle and SLA work in milestone 3 ---

	@Column(name = "first_responded_at")
	private Instant firstRespondedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "sla_response_due_at")
	private Instant slaResponseDueAt;

	@Column(name = "sla_resolution_due_at")
	private Instant slaResolutionDueAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "sla_state", length = 16)
	private SlaState slaState;

	@Column(name = "sla_paused_at")
	private Instant slaPausedAt;

	@Column(name = "total_paused_minutes", nullable = false)
	private int totalPausedMinutes;

	/**
	 * Optimistic locking. Two agents editing the same ticket concurrently means
	 * the second write fails loudly instead of silently discarding the first.
	 */
	@Version
	private long version;

	protected Ticket() {
		// JPA requires a no-arg constructor.
	}

	public Ticket(
			String number,
			TicketType type,
			String title,
			String description,
			Category category,
			Priority priority,
			User requester,
			Instant createdAt) {
		this.number = number;
		this.type = type;
		this.title = title;
		this.description = description;
		this.category = category;
		this.priority = priority;
		this.requester = requester;
		this.createdAt = createdAt;
		this.status = TicketStatus.NEW;
	}

	// --- Mutators for the fields PATCH /api/tickets/{id} may change ---

	public void changeTitle(String title) {
		this.title = title;
	}

	public void changeDescription(String description) {
		this.description = description;
	}

	public void changeCategory(Category category) {
		this.category = category;
	}

	/**
	 * Changing the priority will also need to recalculate the SLA due dates, but
	 * that logic belongs to milestone 3 and is not implemented yet.
	 */
	public void changePriority(Priority priority) {
		this.priority = priority;
	}

	public Long getId() {
		return id;
	}

	public String getNumber() {
		return number;
	}

	public TicketType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public Priority getPriority() {
		return priority;
	}

	public TicketStatus getStatus() {
		return status;
	}

	public User getRequester() {
		return requester;
	}

	public User getAssignee() {
		return assignee;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getFirstRespondedAt() {
		return firstRespondedAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public Instant getSlaResponseDueAt() {
		return slaResponseDueAt;
	}

	public Instant getSlaResolutionDueAt() {
		return slaResolutionDueAt;
	}

	public SlaState getSlaState() {
		return slaState;
	}

	public Instant getSlaPausedAt() {
		return slaPausedAt;
	}

	public int getTotalPausedMinutes() {
		return totalPausedMinutes;
	}

	public long getVersion() {
		return version;
	}
}
