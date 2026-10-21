package com.devtrack.task.service;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.devtrack.shared.exception.ApiException;
import com.devtrack.task.domain.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Maquina de estados de las tareas.
 *
 * <p>Se prueban los 16 pares posibles (estado origen x estado destino) de forma
 * exhaustiva, no solo los casos esperados: el objetivo es que una transicion no prevista
 * rompa un test.
 */
class TaskStatusPolicyTest {

    private final TaskStatusPolicy policy = new TaskStatusPolicy();

    static Stream<Arguments> transiciones() {
        return Stream.of(
                // Desde TODO
                Arguments.of(TaskStatus.TODO, TaskStatus.TODO, true),
                Arguments.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, true),
                Arguments.of(TaskStatus.TODO, TaskStatus.DONE, true),
                Arguments.of(TaskStatus.TODO, TaskStatus.CANCELLED, true),
                // Desde IN_PROGRESS
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.TODO, true),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS, true),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE, true),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED, true),
                // Desde DONE
                Arguments.of(TaskStatus.DONE, TaskStatus.TODO, false),
                Arguments.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, true),
                Arguments.of(TaskStatus.DONE, TaskStatus.DONE, true),
                Arguments.of(TaskStatus.DONE, TaskStatus.CANCELLED, true),
                // Desde CANCELLED
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.TODO, true),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.IN_PROGRESS, false),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.DONE, false),
                Arguments.of(TaskStatus.CANCELLED, TaskStatus.CANCELLED, true));
    }

    @ParameterizedTest(name = "{0} -> {1} permitida: {2}")
    @MethodSource("transiciones")
    @DisplayName("La matriz de transiciones es exacta")
    void transicionEstaPermitida(TaskStatus from, TaskStatus to, boolean allowed) {
        assertThat(policy.isAllowed(from, to)).isEqualTo(allowed);
    }

    @Test
    @DisplayName("Una transicion valida no lanza excepcion")
    void transicionValidaNoFalla() {
        assertThatCode(() -> policy.validate(TaskStatus.TODO, TaskStatus.IN_PROGRESS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Una transicion prohibida falla con 422 INVALID_STATUS_TRANSITION")
    void transicionProhibidaFalla() {
        assertThatThrownBy(() -> policy.validate(TaskStatus.DONE, TaskStatus.TODO))
                .isInstanceOf(ApiException.Unprocessable.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.errorCode().name()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(apiException.errorCode().status().value()).isEqualTo(422);
                })
                .hasMessageContaining("DONE")
                .hasMessageContaining("TODO");
    }

    @Test
    @DisplayName("Las transiciones permitidas desde cada estado son exactamente las definidas")
    void transicionesPermitidasDesdeCadaEstado() {
        assertThat(allowedFrom(TaskStatus.TODO))
                .containsExactlyInAnyOrder(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.CANCELLED);
        assertThat(allowedFrom(TaskStatus.IN_PROGRESS))
                .containsExactlyInAnyOrder(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELLED);
        assertThat(allowedFrom(TaskStatus.DONE))
                .containsExactlyInAnyOrder(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED);
        assertThat(allowedFrom(TaskStatus.CANCELLED)).containsExactlyInAnyOrder(TaskStatus.TODO);
    }

    private Set<TaskStatus> allowedFrom(TaskStatus from) {
        Set<TaskStatus> allowed = EnumSet.noneOf(TaskStatus.class);
        for (TaskStatus candidate : TaskStatus.values()) {
            if (!candidate.equals(from) && policy.isAllowed(from, candidate)) {
                allowed.add(candidate);
            }
        }
        return allowed;
    }
}
