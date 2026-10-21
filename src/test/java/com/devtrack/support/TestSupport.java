package com.devtrack.support;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Utilidades compartidas por los tests de integracion.
 */
public final class TestSupport {

    private TestSupport() {}

    /**
     * Registra un usuario y devuelve su token de acceso.
     *
     * @param mockMvc cliente HTTP de prueba
     * @param email email del usuario
     * @param password contrasena
     * @return token Bearer listo para usar en la cabecera {@code Authorization}
     */
    public static String registerAndLogin(MockMvc mockMvc, String email, String password) throws Exception {
        register(mockMvc, email, password);
        return login(mockMvc, email, password);
    }

    /**
     * Registra un usuario y devuelve su token, con un email aleatorio.
     */
    public static String registerAndLogin(MockMvc mockMvc, String password) throws Exception {
        return registerAndLogin(mockMvc, uniqueEmail(), password);
    }

    /** Registra un usuario y comprueba que la respuesta es 201. */
    public static void register(MockMvc mockMvc, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Usuario de Prueba"}
                                """
                                .formatted(email, password)))
                .andExpect(status().isCreated());
    }

    /** Inicia sesion y devuelve el token de acceso. */
    public static String login(MockMvc mockMvc, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """
                                .formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return (String) JsonPath.read(body, "$.accessToken");
    }

    /** Registra un usuario y devuelve su identificador. */
    public static Long registerAndGetId(MockMvc mockMvc, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"Usuario de Prueba"}
                                """
                                .formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Crea un proyecto con el token indicado y devuelve su identificador. */
    public static Long createProject(MockMvc mockMvc, String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Proyecto de prueba"}
                                """
                                .formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Anade un miembro al proyecto. */
    public static void addMember(MockMvc mockMvc, String token, Long projectId, Long userId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/members", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"role":"%s"}
                                """
                                .formatted(userId, role)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/projects/" + projectId + "/members/")));
    }

    /** Email aleatorio, para que los tests no dependan del estado de la base de datos. */
    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@devtrack.test";
    }

    /** Nombre de proyecto aleatorio. */
    public static String uniqueProjectName() {
        return "Proyecto-" + UUID.randomUUID();
    }

    /** Contrasena valida (10..72 caracteres). */
    public static String validPassword() {
        return "Str0ng-Passphrase!";
    }
}
