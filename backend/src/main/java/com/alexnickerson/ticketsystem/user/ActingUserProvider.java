package com.alexnickerson.ticketsystem.user;

/**
 * Who is making the current request.
 *
 * <p>This interface exists so that the rest of the application never knows how
 * the answer is obtained. Until milestone 2 the only implementation reads a
 * request header, because there is no authentication yet; milestone 2 replaces
 * that single class with one that reads Spring Security's context, and no caller
 * changes.
 */
public interface ActingUserProvider {

	User current();
}
