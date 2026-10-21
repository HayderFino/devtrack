package com.devtrack.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtrack.project.domain.ProjectMember;

/**
 * Repositorio de membresias de proyecto.
 */
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMember> findByProjectIdAndRole(Long projectId, com.devtrack.project.domain.ProjectRole role);

    List<ProjectMember> findByUserIdAndProjectIdIn(Long userId, List<Long> projectIds);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndRole(Long projectId, com.devtrack.project.domain.ProjectRole role);

    /** Miembros de un proyecto, con el usuario traido en la misma consulta (evita N+1). */
    @Query("""
            select pm from ProjectMember pm
            join fetch pm.user
            where pm.project.id = :projectId
            order by case pm.role when com.devtrack.project.domain.ProjectRole.OWNER then 0 else 1 end, pm.createdAt
            """)
    List<ProjectMember> findAllByProjectIdWithUser(@Param("projectId") Long projectId);

    /**
     * Recuento de miembros por proyecto para un conjunto de proyectos.
     *
     * <p>Una sola consulta para todos los proyectos, en lugar de una por proyecto (N+1).
     *
     * @param projectIds identificadores de los proyectos
     * @return pares (projectId, numero de miembros)
     */
    @Query("""
            select pm.project.id, count(pm) from ProjectMember pm
            where pm.project.id in :projectIds
            group by pm.project.id
            """)
    List<Object[]> countMembersByProjectIds(@Param("projectIds") List<Long> projectIds);
}
