package com.malphasos.malphasos.person.infrastructure.input.rest;

import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.infrastructure.input.mapper.PersonRestMapper;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PhonePersonCreateRequest;
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
 * Telefonos de una persona, expuestos como subrecurso suyo.
 *
 * <p>La ruta refleja la relación real: un teléfono no existe sin la persona a la que pertenece. El
 * original los colgaba de {@code /person/phone}, como si fueran un recurso independiente, y usaba
 * PUT para crearlos.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/persons/{personId}/phones")
@Tag(name = "Person phones", description = "Telefonos de una persona")
public class PhonePersonRestAdapter {

    private final PersonServicePort personServicePort;
    private final PersonRestMapper personRestMapper;

    @Operation(summary = "Agregar un telefono a una persona")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<PersonResponse> addPhone(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID personId,
            @Valid @RequestBody PhonePersonCreateRequest request) {

        PersonResponse updated = personRestMapper.toResponse(
                personServicePort.addPhone(personId, personRestMapper.toPhonePerson(request)));

        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @Operation(summary = "Actualizar un telefono de una persona")
    @PreAuthorize("hasAuthority('admin.full')")
    @PutMapping("/{phoneId}")
    public PersonResponse updatePhone(
            @PathVariable UUID personId,
            @Parameter(description = "Identificador del telefono") @PathVariable UUID phoneId,
            @Valid @RequestBody PhonePersonCreateRequest request) {

        return personRestMapper.toResponse(
                personServicePort.updatePhone(personId, phoneId, personRestMapper.toPhonePerson(request)));
    }

    @Operation(
            summary = "Desactivar un telefono",
            description = "No lo elimina: lo marca como inactivo y conserva el historial.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{phoneId}")
    public PersonResponse removePhone(
            @PathVariable UUID personId,
            @Parameter(description = "Identificador del telefono") @PathVariable UUID phoneId) {

        return personRestMapper.toResponse(personServicePort.removePhone(personId, phoneId));
    }
}
