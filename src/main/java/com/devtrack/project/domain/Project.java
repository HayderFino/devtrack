package com.devtrack.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.devtrack.shared.persistence.BaseEntity;
import com.devtrack.user.domain.User;

/**
 * Proyecto.
 *
 * <p>Relaciones <strong>unidireccionales</strong> (regla R-M3): la entidad no mantiene
 * listas de miembros ni de tareas, lo que evita los problemas de serializacion y de
 * {@code MultipleBagFetchException}.
 */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_projects_owner"))
    private User owner;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    protected Project() {}

    public Project(String name, String description, User owner) {
        this.name = name;
        this.description = description;
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public boolean isArchived() {
        return archived;
    }

    public void archive() {
        this.archived = true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Project project)) {
            return false;
        }
        return getId() != null && getId().equals(project.getId());
    }

    @Override
    public int hashCode() {
        return Project.class.hashCode();
    }
}
