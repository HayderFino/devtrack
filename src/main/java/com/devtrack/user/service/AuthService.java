package com.devtrack.user.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devtrack.shared.exception.ApiException;
import com.devtrack.shared.exception.ErrorCode;
import com.devtrack.shared.security.JwtService;
import com.devtrack.user.api.dto.AuthResponse;
import com.devtrack.user.api.dto.LoginRequest;
import com.devtrack.user.api.dto.RegisterRequest;
import com.devtrack.user.api.dto.UserResponse;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;

/**
 * Casos de uso de registro e inicio de sesion.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Crea una cuenta de usuario.
     *
     * @param request datos de alta
     * @return el perfil creado
     */
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException.Conflict(ErrorCode.EMAIL_ALREADY_REGISTERED, "El email ya esta registrado");
        }

        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().strip());
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Autentica a un usuario y emite un token de acceso.
     *
     * <p>El mensaje de error es identico tanto si el email no existe como si la contrasena
     * es incorrecta: no se debe permitir enumerar cuentas registradas (ADR-0003).
     *
     * @param request credenciales
     * @return token de acceso y perfil
     */
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException.Unauthenticated(ErrorCode.INVALID_CREDENTIALS, "Credenciales invalidas");
        }
        if (!user.isEnabled()) {
            throw new ApiException.Forbidden(ErrorCode.ACCOUNT_DISABLED, "La cuenta esta deshabilitada");
        }

        String token = jwtService.issue(user);
        return AuthResponse.from(user, token, jwtService.accessTokenTtl().toSeconds());
    }

    private static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
