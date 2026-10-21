package com.devtrack.task.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.devtrack.project.domain.Project;
import com.devtrack.shared.persistence.BaseEntity;
import com.devtrack.user.domain.User;

/**
 * Tarea de un proyecto.
 *
 * <p>Relaciones unidireccionales (regla R-M3). El campo {@code version} implementa
 * <strong>locking optimista</strong>: dos ediciones concurrentes de la misma tarea no se
 * pisan, la segunda falla con un conflicto (409).
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tasks_project"))
    private Project project;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", foreignKey = @ForeignKey(name = "fk_tasks_assignee"))
    private User assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_tasks_created_by"))
    private User createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Task() {}

    public Task(Project project, String title, String description, User createdBy) {
        this.project = project;
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
    }

    public Project getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Long getVersion() {
        return version;
    }

    /** Indica si la tarea ha superado su fecha limite y sigue sin completarse. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today) && status != TaskStatus.DONE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Task task)) {
            return false;
        }
        return getId() != null && getId().equals(task.getId());
    }

    @Override
    public int hashCode() {
        return Task.class.hashCode();
    }
}
