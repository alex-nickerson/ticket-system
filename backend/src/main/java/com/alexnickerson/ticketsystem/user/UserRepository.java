package com.alexnickerson.ticketsystem.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	List<User> findByRoleOrderByDisplayNameAsc(UserRole role);
}
