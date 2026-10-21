package com.devtrack.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.devtrack.project.domain.Project;

/**
 * Repositorio de proyectos.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Proyectos de los que el usuario es miembro, opcionalmente filtrando por archivado.
     *
     * <p>El filtro se resuelve en SQL: filtrar en memoria romperia la paginacion.
     *
     * @param userId usuario autenticado
     * @param archived {@code true} solo archivados, {@code false} solo activos, {@code null} ambos
     * @param pageable paginacion
     * @return pagina de proyectos
     */
    @Query("""
            select p from Project p
            join fetch p.owner
            join ProjectMember pm on pm.project.id = p.id
            where pm.user.id = :userId and (:archived is null or p.archived = :archived)
            """)
    Page<Project> findAllForUser(@Param("userId") Long userId, @Param("archived") Boolean archived, Pageable pageable);
}
