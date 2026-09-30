package com.alexnickerson.ticketsystem.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

	/**
	 * Every piece of time logic depends on this bean rather than calling
	 * {@code Instant.now()} directly, so tests can substitute a fixed clock. The
	 * SLA work in milestone 3 relies on that heavily; starting now means there is
	 * no scattered {@code now()} to hunt down later.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
