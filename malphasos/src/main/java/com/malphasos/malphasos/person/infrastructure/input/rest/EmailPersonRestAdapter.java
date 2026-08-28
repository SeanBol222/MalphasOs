package com.malphasos.malphasos.person.infrastructure.input.rest;

import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.infrastructure.input.mapper.PersonRestMapper;
import com.malphasos.malphasos.person.infrastructure.input.model.request.EmailPersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.response.PersonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correos de una persona, expuestos como subrecurso suyo.
 *
 * <p>La ruta refleja la relación real: un correo no existe sin la persona a la que pertenece. El
 * original los colgaba de {@code /person/email}, como si fueran un recurso independiente, y usaba
 * PUT para crearlos.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/persons/{personId}/emails")
@Tag(name = "Person emails", description = "Correos de una persona")
public class EmailPersonRestAdapter {

    private final PersonServicePort personServicePort;
    private final PersonRestMapper personRestMapper;

    @Operation(summary = "Agregar un correo a una persona")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<PersonResponse> addEmail(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID personId,
            @Valid @RequestBody EmailPersonCreateRequest request) {

        PersonResponse updated = personRestMapper.toResponse(
                personServicePort.addEmail(personId, personRestMapper.toEmailPerson(request)));

        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @Operation(summary = "Actualizar un correo de una persona")
    @PreAuthorize("hasAuthority('admin.full')")
    @PutMapping("/{emailId}")
    public PersonResponse updateEmail(
            @PathVariable UUID personId,
            @Parameter(description = "Identificador del correo") @PathVariable UUID emailId,
            @Valid @RequestBody EmailPersonCreateRequest request) {

        return personRestMapper.toResponse(
                personServicePort.updateEmail(personId, emailId, personRestMapper.toEmailPerson(request)));
    }

    @Operation(
            summary = "Desactivar un correo",
            description = "No lo elimina: lo marca como inactivo y conserva el historial.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{emailId}")
    public PersonResponse removeEmail(
            @PathVariable UUID personId,
            @Parameter(description = "Identificador del correo") @PathVariable UUID emailId) {

        return personRestMapper.toResponse(personServicePort.removeEmail(personId, emailId));
    }
}
