package com.devtrack.user.api;

import com.devtrack.support.IntegrationTest;
import com.devtrack.support.TestSupport;
import com.devtrack.user.domain.User;
import com.devtrack.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de autenticacion: registro, login y acceso a un recurso protegido.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Registra un usuario y devuelve 201 con su perfil")
    void registerReturnsCreated() throws Exception {
        String email = TestSupport.uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Ada Lovelace"}
                                """
                                .formatted(email, TestSupport.validPassword())))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.platformRole").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Registra un email duplicado y devuelve 409")
    void registerDuplicateEmailReturnsConflict() throws Exception {
        String email = TestSupport.uniqueEmail();
        TestSupport.register(mockMvc, email, TestSupport.validPassword());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Duplicado"}
                                """
                                .formatted(email, TestSupport.validPassword())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Normaliza el email a minusculas")
    void registerNormalizesEmail() throws Exception {
        String email = TestSupport.uniqueEmail().toUpperCase();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Mayusculas"}
                                """
                                .formatted(email, TestSupport.validPassword())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));
    }

    @Test
    @DisplayName("Devuelve 400 con el detalle por campo cuando la validacion falla")
    void registerWithInvalidPayloadReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"no-es-un-email","password":"corta","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName')]").exists());
    }

    @Test
    @DisplayName("Inicia sesion y devuelve un token de acceso")
    void loginReturnsToken() throws Exception {
        String email = TestSupport.uniqueEmail();
        TestSupport.register(mockMvc, email, TestSupport.validPassword());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, TestSupport.validPassword())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("Devuelve 401 con credenciales incorrectas")
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String email = TestSupport.uniqueEmail();
        TestSupport.register(mockMvc, email, TestSupport.validPassword());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ContrasenaIncorrecta!"}
                                """
                                .formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Devuelve el mismo error con email inexistente que con contrasena incorrecta")
    void loginDoesNotRevealWhetherEmailExists() throws Exception {
        String existing = TestSupport.uniqueEmail();
        TestSupport.register(mockMvc, existing, TestSupport.validPassword());

        String wrongPasswordBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ContrasenaIncorrecta!"}
                                """
                                .formatted(existing)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownEmailBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"ContrasenaIncorrecta!"}
                                """
                                .formatted(TestSupport.uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // El cuerpo solo puede diferir en la marca de tiempo.
        assertThat(withoutTimestamp(unknownEmailBody)).isEqualTo(withoutTimestamp(wrongPasswordBody));
    }

    @Test
    @DisplayName("El token emitido permite acceder a un recurso protegido")
    void issuedTokenGrantsAccess() throws Exception {
        String token = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.platformRole").value("USER"));
    }

    @Test
    @DisplayName("Sin token, un recurso protegido devuelve 401 con ProblemDetail")
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/me"));
    }

    @Test
    @DisplayName("Un token con firma manipulada devuelve 401")
    void tamperedTokenReturnsUnauthorized() throws Exception {
        String token = TestSupport.registerAndLogin(mockMvc, TestSupport.validPassword());
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Un token malformado devuelve 401")
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Una cabecera Authorization con otro esquema no autentica")
    void nonBearerSchemeDoesNotAuthenticate() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un token deja de ser valido cuando la cuenta se deshabilita")
    void disabledAccountTokenReturnsUnauthorized() throws Exception {
        String email = TestSupport.uniqueEmail();
        String token = TestSupport.registerAndLogin(mockMvc, email, TestSupport.validPassword());
        disable(email);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Una cuenta deshabilitada no puede iniciar sesion")
    void disabledAccountCannotLogin() throws Exception {
        String email = TestSupport.uniqueEmail();
        TestSupport.register(mockMvc, email, TestSupport.validPassword());
        disable(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, TestSupport.validPassword())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("La respuesta de error no filtra detalles internos")
    void errorResponseDoesNotLeakInternals() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Se requiere autenticacion valida"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    @DisplayName("El registro devuelve el perfil creado sin datos sensibles")
    void registerReturnsCreatedProfile() throws Exception {
        String email = TestSupport.uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Con Location"}
                                """
                                .formatted(email, TestSupport.validPassword())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email));
    }

    private void disable(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);
    }

    /**
     * Elimina la marca de tiempo para comparar dos cuerpos de error que deben ser
     * identicos salvo por el instante en que se generaron.
     */
    private static String withoutTimestamp(String body) {
        return body.replaceAll("\"timestamp\":\"[^\"]+\"", "\"timestamp\":\"?\"");
    }
}
