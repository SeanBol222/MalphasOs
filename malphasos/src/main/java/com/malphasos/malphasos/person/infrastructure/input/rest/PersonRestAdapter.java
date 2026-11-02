package com.malphasos.malphasos.person.infrastructure.input.rest;

import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.infrastructure.input.mapper.PersonRestMapper;
import com.malphasos.malphasos.person.infrastructure.input.model.request.AccountGrantRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PasswordResetRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonRegisterRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonUpdateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.response.PersonAccountResponse;
import com.malphasos.malphasos.person.infrastructure.input.model.response.PersonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de personas.
 *
 * <p>Todas las rutas cuelgan de {@code /v1/api/persons}, siguiendo la convención del proyecto. El
 * controlador original repartía sus operaciones entre tres versiones distintas del API —{@code /vi/}
 * por un error tipográfico, {@code /v1/} y {@code /v2/}— sobre una base {@code /person}.
 *
 * <p>Los métodos declaran el tipo concreto que devuelven y no {@code Object} ni {@code List<?>} como
 * en el original: sin eso, la documentación de OpenAPI no puede describir la respuesta.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/persons")
@Tag(name = "Person", description = "Gestion de personas")
public class PersonRestAdapter {

    private final PersonServicePort personServicePort;
    private final PersonRestMapper personRestMapper;

    @Operation(summary = "Listar todas las personas")
    @PreAuthorize("hasAuthority('person.read')")
    @GetMapping
    public List<PersonResponse> getAllPersons() {
        return personRestMapper.toResponseList(personServicePort.findAll());
    }

    @Operation(summary = "Obtener una persona por su identificador")
    @PreAuthorize("hasAuthority('person.read')")
    @GetMapping("/{id}")
    public PersonResponse getPersonById(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID id) {

        return personRestMapper.toResponse(personServicePort.findById(id));
    }

    @Operation(
            summary = "Registrar un ingeniero",
            description = "Crea la persona y su usuario en el proveedor de identidad.")
    @PreAuthorize("hasAuthority('super.person.write')")
    @PostMapping("/engineers")
    public ResponseEntity<PersonResponse> registerEngineer(
            @Valid @RequestBody PersonRegisterRequest request) {

        return created(personServicePort.registerEngineer(personRestMapper.toUseCaseRequest(request)));
    }

    @Operation(
            summary = "Registrar un administrador",
            description = "Crea la persona y su usuario en el proveedor de identidad.")
    @PreAuthorize("hasAuthority('super.person.write')")
    @PostMapping("/admins")
    public ResponseEntity<PersonResponse> registerAdmin(
            @Valid @RequestBody PersonRegisterRequest request) {

        return created(personServicePort.registerAdmin(personRestMapper.toUseCaseRequest(request)));
    }

    @Operation(
            summary = "Registrar un supervisor de cliente",
            description = "Crea la persona y su usuario en el proveedor de identidad. Lo que ve lo decide "
                    + "a que clientes se le nombra supervisor, desde la ficha de cada uno.")
    @PreAuthorize("hasAuthority('person.write')")
    @PostMapping("/supervisors")
    public ResponseEntity<PersonResponse> registerSupervisor(
            @Valid @RequestBody PersonRegisterRequest request) {

        return created(personServicePort.registerSupervisor(personRestMapper.toUseCaseRequest(request)));
    }

    @Operation(summary = "Actualizar los datos de una persona")
    @PreAuthorize("@personWriteGuard.canUpdate(#id, #request.tipoPersona(), authentication)")
    @PutMapping("/{id}")
    public PersonResponse updatePerson(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID id,
            @Valid @RequestBody PersonUpdateRequest request) {

        return personRestMapper.toResponse(
                personServicePort.update(id, personRestMapper.toPerson(request)));
    }

    @Operation(
            summary = "Desactivar una persona",
            description = "No la elimina: la marca como inactiva y conserva su historial.")
    @PreAuthorize("@personWriteGuard.canWrite(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID id) {

        personServicePort.delete(id);

        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<PersonResponse> created(
            com.malphasos.malphasos.person.domain.person.Person person) {

        return ResponseEntity.status(HttpStatus.CREATED).body(personRestMapper.toResponse(person));
    }

    @Operation(
            summary = "Restablecer la contraseña",
            description = "Pone una contraseña temporal en su cuenta: al entrar, la persona tendrá que "
                    + "cambiarla. 400 si no cumple la política; 404 si la persona no tiene cuenta.")
    @PreAuthorize("@personWriteGuard.canWrite(#id, authentication)")
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID id,
            @Valid @RequestBody PasswordResetRequest request) {

        personServicePort.resetPassword(id, request.password());

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Consultar la cuenta de una persona",
            description = "Si tiene usuario en el proveedor de identidad y cuál. No tenerlo no es un error.")
    @PreAuthorize("hasAuthority('person.read')")
    @GetMapping("/{id}/account")
    public PersonAccountResponse getAccount(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID id) {

        return personServicePort.accountOf(id)
                .map(usuario -> new PersonAccountResponse(true, usuario))
                .orElseGet(() -> new PersonAccountResponse(false, null));
    }

    @Operation(
            summary = "Dar acceso a una persona sin cuenta",
            description = "Crea su usuario con una contraseña temporal y el correo de la ficha. El "
                    + "identificador de la persona cambia al del usuario nuevo, y la respuesta lo trae. "
                    + "409 si ya tiene cuenta; 400 si no tiene correo o está retirada.")
    @PreAuthorize("@personWriteGuard.canWrite(#id, authentication)")
    @PostMapping("/{id}/account")
    public ResponseEntity<PersonResponse> grantAccess(
            @Parameter(description = "Identificador de la persona") @PathVariable UUID id,
            @Valid @RequestBody AccountGrantRequest request) {

        return created(personServicePort.grantAccess(id, request.nombreUsuario(), request.password()));
    }
}
