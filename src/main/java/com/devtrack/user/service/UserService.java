package com.devtrack.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.user.api.dto.UserResponse;
import com.devtrack.user.repository.UserRepository;

/**
 * Consultas sobre el perfil de usuario.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Obtiene el perfil de un usuario.
     *
     * @param userId identificador del usuario
     * @return el perfil
     */
    public UserResponse getById(Long userId) {
        return userRepository
                .findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() ->
                        new ApiException.ResourceNotFound(ErrorCode.USER_NOT_FOUND, "No existe un usuario con id " + userId));
    }
}
