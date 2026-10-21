package com.devtrack.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devtrack.user.domain.User;

/**
 * Repositorio de usuarios.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Comprueba que la cuenta existe y sigue habilitada, sin cargar la entidad. */
    boolean existsByIdAndEnabledTrue(Long id);
}
