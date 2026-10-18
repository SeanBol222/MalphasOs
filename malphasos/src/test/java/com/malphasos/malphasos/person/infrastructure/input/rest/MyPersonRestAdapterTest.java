package com.malphasos.malphasos.person.infrastructure.input.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Los datos de quien llama: la persona sale del token, nunca de la ruta. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyPersonRestAdapterTest {

    private static final String RUTA = "/v1/api/persons/me";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PersonServicePort servicio;

    @Test
    @DisplayName("GET devuelve la persona del token, y no la confunde con /persons/{id}")
    void misDatos() throws Exception {
        // «me» no es un UUID: si cayera en /{id}, respondería 400 al convertir la ruta.
        UUID yo = UUID.randomUUID();
        Person persona = new Person();
        persona.setIdentificador(yo);
        persona.setCedula("1000000000");
        persona.setPrimerNombre("Ada");
        persona.setPrimerApellido("Lovelace");
        persona.setTipoPersona(PersonType.ENGINEER);
        persona.setEstadoActivo(true);
        when(servicio.findById(yo)).thenReturn(persona);

        mockMvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject(yo.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificador").value(yo.toString()))
                .andExpect(jsonPath("$.cedula").value("1000000000"))
                .andExpect(jsonPath("$.tipoPersona").value("ENGINEER"));
    }

    @Test
    @DisplayName("una sesión que no es de una persona registrada responde 404 con su código")
    void sinPersona() throws Exception {
        mockMvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject("operador-de-keycloak"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_001"));

        verify(servicio, never()).findById(any());
    }

    @Test
    @DisplayName("una cuenta de Keycloak sin fila en persona responde 404, no 500")
    void cuentaSinFila() throws Exception {
        // El SuperUsuario: su sub es un UUID, pero ninguna persona lo tiene.
        UUID yo = UUID.randomUUID();
        when(servicio.findById(yo)).thenThrow(new PersonNotFoundException("no existe"));

        mockMvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject(yo.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_001"));
    }
}
