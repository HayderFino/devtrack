package com.devtrack.user.domain;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.devtrack.shared.persistence.BaseEntity;

/**
 * Cuenta de usuario.
 *
 * <p>La contrasena nunca se almacena en claro: {@code passwordHash} contiene un hash BCrypt.
 * El campo no se expone en ningun DTO de salida.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 20)
    private PlatformRole platformRole = PlatformRole.USER;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected User() {}

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    /**
     * Normaliza el email a minusculas antes de persistir.
     *
     * <p>La unicidad la garantiza la restriccion {@code uq_users_email}; normalizar solo
     * hace que el mensaje de error sea coherente y evita duplicados por mayusculas.
     */
    @PrePersist
    @PreUpdate
    void normalizeEmail() {
        if (email != null) {
            this.email = email.strip().toLowerCase(Locale.ROOT);
        }
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }

    public void setPlatformRole(PlatformRole platformRole) {
        this.platformRole = platformRole;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Identidad por clave primaria.
     *
     * <p>El hash code es constante a proposito (regla R-M4): las entidades pueden mutar
     * durante su ciclo de vida y un hash dependiente del estado romperia las colecciones
     * que las contienen.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return getId() != null && getId().equals(user.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}
