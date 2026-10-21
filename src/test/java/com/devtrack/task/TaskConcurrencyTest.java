package com.devtrack.task;

import com.devtrack.shared.exception.GlobalExceptionHandler;
import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import com.devtrack.task.domain.Task;
import com.devtrack.task.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba determinista del locking optimista (R-07).
 *
 * <p>No usa una carrera real entre hilos: carga dos copias desacopladas de la misma tarea,
 * guarda la primera y comprueba que la segunda, con la version obsoleta, no puede
 * sobrescribirla.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TaskConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DisplayName("Guardar una tarea con una version obsoleta falla con conflicto de locking")
    void staleVersionFailsWithOptimisticLock() throws Exception {
        String ownerToken = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        Long projectId = TestSupport.createProject(mockMvc, ownerToken, TestSupport.uniqueProjectName());
        Long taskId = TestSupport.createTask(mockMvc, ownerToken, projectId, "Tarea concurrente");

        Task first = taskRepository.findById(taskId).orElseThrow();
        Task stale = taskRepository.findById(taskId).orElseThrow();

        first.setTitle("Primera escritura");
        taskRepository.saveAndFlush(first);

        stale.setTitle("Escritura obsoleta");

        assertThatThrownBy(() -> taskRepository.saveAndFlush(stale))
                .isInstanceOfSatisfying(OptimisticLockingFailureException.class, ex -> {
                    MockHttpServletRequest request =
                            new MockHttpServletRequest("PATCH", "/api/v1/projects/" + projectId + "/tasks/" + taskId);
                    ResponseEntity<ProblemDetail> response =
                            new GlobalExceptionHandler().handleOptimisticLock(ex, request);

                    assertThat(response.getStatusCode().value()).isEqualTo(409);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getProperties().get("code")).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
                });
    }
}
