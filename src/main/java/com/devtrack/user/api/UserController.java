package com.devtrack.user.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devtrack.shared.security.CurrentUser;
import com.devtrack.user.api.dto.UserResponse;
import com.devtrack.user.service.UserService;

/**
 * Endpoints de consulta de usuarios.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Perfil del usuario autenticado.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userService.getById(currentUser.userId());
    }
}
