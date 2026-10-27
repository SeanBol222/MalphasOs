package com.malphasos.malphasos.client.infrastructure.input.rest;

import com.malphasos.malphasos.client.application.ports.input.ManagerServicePort;
import com.malphasos.malphasos.client.application.services.manager.commands.AssignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.DeactivateManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RegisterManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RelieveManagerCommand;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
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
 *
 * <p><b>Desde el 2026-10-27 un encargado está a cargo de varias sedes y áreas</b> (`V41`), todas del
 * mismo cliente. {@code POST /managers/assignments} suma una a las que ya tiene, y se le releva de cada
 * una por separado, en su sub-recurso. El traslado se retiró: era relevar de una y asignar otra.
 *
 * <p><b>Sobre las autoridades {@code engineer.*}.</b> No es un error que un controlador llamado
 * {@code Manager} exija permisos llamados {@code engineer}: son tres nombres para lo mismo. El
 * realm de Keycloak lo llama {@code engineer}, el código {@code manager} y la ERS «profesional
 * responsable». Se usa el vocabulario del realm porque es el que la documentación de despliegue ya
 * recoge, y renombrarlo obligaría a tocar el realm, los grupos y los tokens ya emitidos. Leer un
 * encargado exige {@code engineer.read}; ponerlo o relevarlo, {@code engineer.assign}, porque las
 * dos son la misma decisión: quién queda al frente de qué.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/managers")
@Tag(name = "Manager", description = "Gestion de encargados de sedes y areas")
public class ManagerRestAdapter {

    private final ManagerServicePort managerServicePort;
    private final ClientRestMapper clientRestMapper;

    @Operation(summary = "Listar todos los encargados")
    @PreAuthorize("hasAuthority('engineer.read')")
    @GetMapping
    public List<ManagerResponse> getAllManagers() {
        return clientRestMapper.toManagerResponseList(managerServicePort.findAll());
    }

    @Operation(summary = "Obtener el encargado que corresponde a una persona")
    @PreAuthorize("hasAuthority('engineer.read')")
    @GetMapping("/{idPersona}")
    public ManagerResponse getByPerson(
            @Parameter(description = "Identificador de la persona, que es el del encargado")
            @PathVariable UUID idPersona) {

        return clientRestMapper.toResponse(managerServicePort.findByPerson(idPersona));
    }

    @Operation(
            summary = "Registrar un encargado nuevo",
            description = "Crea la persona y la pone al frente de la sede o area indicada.")
    @PreAuthorize("hasAuthority('engineer.assign')")
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
            description = "Para una persona ya registrada en el sistema. Si ya es encargado, se suma a "
                    + "lo que tiene a cargo, que tiene que ser del mismo cliente.")
    @PreAuthorize("hasAuthority('engineer.assign')")
    @PostMapping("/assignments")
    public ResponseEntity<ManagerResponse> assignManager(
            @Valid @RequestBody ManagerAssignRequest request) {

        ManagerResponse creado = clientRestMapper.toResponse(
                managerServicePort.assign(new AssignManagerCommand(
                        request.idPersona(), request.tipo(), request.idAsignacion())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // El traslado —PATCH /{idPersona}— se retiró el 2026-10-27: con varias asignaciones, trasladar es
    // relevar de una y asignar otra, y las dos operaciones existen por separado.

    @Operation(
            summary = "Relevar a un encargado de una sede",
            description = "Solo de esa: sigue a cargo de lo demás. No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('engineer.assign')")
    @DeleteMapping("/{idPersona}/headquarters/{idSede}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relieveFromHeadquarter(@PathVariable UUID idPersona, @PathVariable UUID idSede) {
        managerServicePort.relieve(
                new RelieveManagerCommand(idPersona, ManagerType.HEADQUARTER, idSede));
    }

    @Operation(
            summary = "Relevar a un encargado de un area de servicio",
            description = "Solo de esa: sigue a cargo de lo demás. No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('engineer.assign')")
    @DeleteMapping("/{idPersona}/service-areas/{idArea}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void relieveFromServiceArea(@PathVariable UUID idPersona, @PathVariable UUID idArea) {
        managerServicePort.relieve(
                new RelieveManagerCommand(idPersona, ManagerType.SERVICE_AREA, idArea));
    }

    @Operation(
            summary = "Relevar a un encargado de todo",
            description = "No lo borra: deja inactivas todas sus asignaciones.")
    @PreAuthorize("hasAuthority('engineer.assign')")
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
