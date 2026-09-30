package com.alexnickerson.ticketsystem.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Null until the user has signed in through Entra ID, so always null in dev. */
	@Column(name = "entra_object_id", length = 64)
	private String entraObjectId;

	@Column(name = "display_name", nullable = false, length = 120)
	private String displayName;

	@Column(nullable = false, length = 254)
	private String email;

	/**
	 * Stored as the enum name rather than its ordinal: ordinals break as soon as
	 * anyone reorders the enum, and the column is far easier to read in psql.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private UserRole role;

	@Column(nullable = false)
	private boolean active = true;

	protected User() {
		// JPA requires a no-arg constructor.
	}

	public User(String displayName, String email, UserRole role) {
		this.displayName = displayName;
		this.email = email;
		this.role = role;
	}

	public Long getId() {
		return id;
	}

	public String getEntraObjectId() {
		return entraObjectId;
	}

	public void setEntraObjectId(String entraObjectId) {
		this.entraObjectId = entraObjectId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getEmail() {
		return email;
	}

	public UserRole getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}
}
