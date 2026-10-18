package com.malphasos.malphasos.person.infrastructure.input.rest;

import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.infrastructure.input.mapper.PersonRestMapper;
import com.malphasos.malphasos.person.infrastructure.input.model.response.PersonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los datos de quien llama, para la ventana de usuario del frontend (2026-10-17).
 *
 * <p><b>Solo los propios</b>, como la firma: la ruta no lleva identificador y la persona sale del token.
 * Hace falta porque {@code GET /persons/{id}} exige {@code person.read}, y un representante de cliente
 * no la tiene: no podía ver ni su propio correo.
 *
 * <p>Pide {@code report.read} por la misma razón que la firma: todo {@code GET} pide una autoridad de
 * lectura —lo exige {@code RestAuthorizationCoverageTest}—, y esa la tienen los tres grupos del realm.
 * Como solo se lee la propia fila, no abre nada ajeno.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/persons/me")
@Tag(name = "MyPerson", description = "Los datos de quien llama")
public class MyPersonRestAdapter {

    private final PersonServicePort personServicePort;
    private final PersonRestMapper personRestMapper;

    @Operation(
            summary = "Mis datos",
            description = "404 si la sesión no corresponde a ninguna persona registrada, como el SuperUsuario.")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping
    public PersonResponse get(Authentication autenticacion) {
        return personRestMapper.toResponse(personServicePort.findById(SessionPerson.of(autenticacion)));
    }
}
