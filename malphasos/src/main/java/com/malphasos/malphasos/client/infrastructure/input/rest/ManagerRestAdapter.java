package com.malphasos.malphasos.client.infrastructure.input.rest;

import com.malphasos.malphasos.client.application.ports.input.ManagerServicePort;
import com.malphasos.malphasos.client.application.services.manager.commands.AssignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.DeactivateManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.ReassignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RegisterManagerCommand;
import com.malphasos.malphasos.client.infrastructure.input.mapper.ClientRestMapper;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ContactRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ManagerAssignRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ManagerRegisterRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ManagerResponse;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PhonePersonUseCaseRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de encargados de sedes y áreas de servicio.
 *
 * <p>Las rutas usan el identificador de la persona, porque es también el del encargado: no hay
 * identidad propia que direccionar.
 *
 * <p>Hay dos altas. {@code POST /managers} registra a alguien que no existe todavía como persona y
 * la crea; {@code POST /managers/assignments} pone al frente a alguien que ya existe. El original
 * solo tenía la primera, de modo que un ingeniero de la empresa no podía figurar además como
 * encargado sin duplicarse.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/managers")
@Tag(name = "Manager", description = "Gestion de encargados de sedes y areas")
public class ManagerRestAdapter {

    private final ManagerServicePort managerServicePort;
    private final ClientRestMapper clientRestMapper;

    @Operation(summary = "Listar todos los encargados")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping
    public List<ManagerResponse> getAllManagers() {
        return clientRestMapper.toManagerResponseList(managerServicePort.findAll());
    }

    @Operation(summary = "Obtener el encargado que corresponde a una persona")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/{idPersona}")
    public ManagerResponse getByPerson(
            @Parameter(description = "Identificador de la persona, que es el del encargado")
            @PathVariable UUID idPersona) {

        return clientRestMapper.toResponse(managerServicePort.findByPerson(idPersona));
    }

    @Operation(
            summary = "Registrar un encargado nuevo",
            description = "Crea la persona y la pone al frente de la sede o area indicada.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<ManagerResponse> registerManager(
            @Valid @RequestBody ManagerRegisterRequest request) {

        ManagerResponse creado = clientRestMapper.toResponse(
                managerServicePort.register(new RegisterManagerCommand(
                        aPeticionDePersona(request), request.tipo(), request.idAsignacion())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(
            summary = "Poner al frente a alguien que ya existe",
            description = "Para una persona ya registrada en el sistema.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/assignments")
    public ResponseEntity<ManagerResponse> assignManager(
            @Valid @RequestBody ManagerAssignRequest request) {

        ManagerResponse creado = clientRestMapper.toResponse(
                managerServicePort.assign(new AssignManagerCommand(
                        request.idPersona(), request.tipo(), request.idAsignacion())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Trasladar un encargado a otra sede o area")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/{idPersona}")
    public ManagerResponse reassignManager(
            @PathVariable UUID idPersona, @Valid @RequestBody ManagerAssignRequest request) {

        return clientRestMapper.toResponse(managerServicePort.reassign(
                new ReassignManagerCommand(idPersona, request.tipo(), request.idAsignacion())));
    }

    @Operation(summary = "Relevar a un encargado", description = "No lo borra: lo deja inactivo.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{idPersona}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateManager(@PathVariable UUID idPersona) {
        managerServicePort.deactivate(new DeactivateManagerCommand(idPersona));
    }

    /**
     * El tipo de persona no se traslada desde la petición: lo fija el servicio, porque por esta vía
     * siempre se registra un encargado.
     */
    private PersonCommunicationRequest aPeticionDePersona(ManagerRegisterRequest request) {
        return PersonCommunicationRequest.builder()
                .cedula(request.cedula())
                .primerNombre(request.primerNombre())
                .segundoNombre(request.segundoNombre())
                .primerApellido(request.primerApellido())
                .segundoApellido(request.segundoApellido())
                .emailPersonList(request.correos().stream()
                        .map(correo -> new EmailPersonUseCaseRequest(correo.valor()))
                        .toList())
                .phonePersonList(telefonos(request.telefonos()))
                .build();
    }

    private List<PhonePersonUseCaseRequest> telefonos(List<ContactRequest> telefonos) {
        return telefonos == null
                ? List.of()
                : telefonos.stream()
                        .map(telefono -> new PhonePersonUseCaseRequest(telefono.valor()))
                        .toList();
    }
}
