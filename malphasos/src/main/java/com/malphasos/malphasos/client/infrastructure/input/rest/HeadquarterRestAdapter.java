package com.malphasos.malphasos.client.infrastructure.input.rest;

import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.DeactivateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.UpdateHeadquarterCommand;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.infrastructure.input.mapper.ClientRestMapper;
import com.malphasos.malphasos.client.infrastructure.input.model.request.HeadquarterCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.HeadquarterUpdateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.response.HeadquarterResponse;
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
 * API de sedes.
 *
 * <p>El alta cuelga de la ruta del cliente, porque una sede no existe sin él. Las demás operaciones
 * usan la ruta propia de la sede: una vez creada, tiene identidad suficiente para direccionarse
 * sola.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
@Tag(name = "Headquarter", description = "Gestion de sedes de clientes")
public class HeadquarterRestAdapter {

    private final HeadquarterServicePort headquarterServicePort;
    private final ClientRestMapper clientRestMapper;

    @Operation(summary = "Listar las sedes de un cliente")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/clients/{idCliente}/headquarters")
    public List<HeadquarterResponse> getByClient(
            @Parameter(description = "Identificador del cliente") @PathVariable UUID idCliente) {

        return clientRestMapper.toHeadquarterResponseList(
                headquarterServicePort.findByClient(idCliente));
    }

    @Operation(summary = "Abrir una sede para un cliente")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/clients/{idCliente}/headquarters")
    public ResponseEntity<HeadquarterResponse> createHeadquarter(
            @PathVariable UUID idCliente, @Valid @RequestBody HeadquarterCreateRequest request) {

        HeadquarterResponse creada = clientRestMapper.toResponse(
                headquarterServicePort.create(new CreateHeadquarterCommand(
                        request.nombre(),
                        new Address(request.calle(), request.carrera(), request.numero()),
                        idCliente,
                        request.idCiudad())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Obtener una sede por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/headquarters/{id}")
    public HeadquarterResponse getById(@PathVariable UUID id) {
        return clientRestMapper.toResponse(headquarterServicePort.findById(id));
    }

    @Operation(
            summary = "Cambiar los datos de una sede",
            description = "Los campos ausentes conservan su valor. La direccion va entera o no va.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/headquarters/{id}")
    public HeadquarterResponse updateHeadquarter(
            @PathVariable UUID id, @Valid @RequestBody HeadquarterUpdateRequest request) {

        return clientRestMapper.toResponse(headquarterServicePort.update(
                new UpdateHeadquarterCommand(id, request.nombre(), direccion(request), request.idCiudad())));
    }

    @Operation(summary = "Cerrar una sede", description = "No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/headquarters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateHeadquarter(@PathVariable UUID id) {
        headquarterServicePort.deactivate(new DeactivateHeadquarterCommand(id));
    }

    /**
     * Las tres partes de la dirección forman un valor único: o vienen todas o no viene ninguna.
     * Recibir solo la calle dejaría una dirección a medias, así que se rechaza.
     */
    private Address direccion(HeadquarterUpdateRequest request) {
        boolean ninguna = request.calle() == null && request.carrera() == null && request.numero() == null;
        if (ninguna) {
            return null;
        }

        boolean todas = request.calle() != null && request.carrera() != null && request.numero() != null;
        if (!todas) {
            throw new IllegalArgumentException(
                    "La direccion se cambia entera: calle, carrera y numero, o ninguna de las tres");
        }

        return new Address(request.calle(), request.carrera(), request.numero());
    }
}
