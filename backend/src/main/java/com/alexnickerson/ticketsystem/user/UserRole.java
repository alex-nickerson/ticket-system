package com.alexnickerson.ticketsystem.user;

/**
 * Application roles. In the prod profile these come from Entra ID app roles;
 * in the dev profile they are set on the seeded users.
 */
public enum UserRole {
	REQUESTER,
	AGENT,
	ADMIN
}
