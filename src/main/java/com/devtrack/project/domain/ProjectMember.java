package com.devtrack.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.devtrack.shared.persistence.BaseEntity;
import com.devtrack.user.domain.User;

/**
 * Membresia de un usuario en un proyecto.
 *
 * <p>Esta tabla es la <strong>fuente de verdad de la autorizacion</strong> (ADR-0004). El
 * proyecto tiene exactamente un miembro con rol {@code OWNER}; esa invariante la garantiza
 * la capa de servicio, porque SQL estandar no puede expresar "exactamente uno".
 */
@Entity
@Table(
        name = "project_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_members", columnNames = {"project_id", "user_id"}))
public class ProjectMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pm_project"))
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_pm_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ProjectRole role;

    protected ProjectMember() {}

    public ProjectMember(Project project, User user, ProjectRole role) {
        this.project = project;
        this.user = user;
        this.role = role;
    }

    public Project getProject() {
        return project;
    }

    public User getUser() {
        return user;
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProjectMember member)) {
            return false;
        }
        return getId() != null && getId().equals(member.getId());
    }

    @Override
    public int hashCode() {
        return ProjectMember.class.hashCode();
    }
}
