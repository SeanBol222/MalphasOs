package com.malphasos.malphasos.client.infrastructure.input.rest;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.CreateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.DeactivateServiceAreaCommand;
import com.malphasos.malphasos.client.application.services.serviceArea.commands.RenameServiceAreaCommand;
import com.malphasos.malphasos.client.infrastructure.input.mapper.ClientRestMapper;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ServiceAreaCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ServiceAreaResponse;
import io.swagger.v3.oas.annotations.Operation;
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

/** API de áreas de servicio. El alta cuelga de la sede: un área no existe fuera de una. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
@Tag(name = "ServiceArea", description = "Gestion de areas de servicio")
public class ServiceAreaRestAdapter {

    private final ServiceAreaServicePort serviceAreaServicePort;
    private final ClientRestMapper clientRestMapper;

    @Operation(summary = "Listar las areas de una sede")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/headquarters/{idSede}/service-areas")
    public List<ServiceAreaResponse> getByHeadquarter(@PathVariable UUID idSede) {
        return clientRestMapper.toServiceAreaResponseList(
                serviceAreaServicePort.findByHeadquarter(idSede));
    }

    @Operation(
            summary = "Abrir un area de servicio en una sede",
            description = "La sede debe estar activa: no se abre un area en una sede cerrada.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/headquarters/{idSede}/service-areas")
    public ResponseEntity<ServiceAreaResponse> createServiceArea(
            @PathVariable UUID idSede, @Valid @RequestBody ServiceAreaCreateRequest request) {

        ServiceAreaResponse creada = clientRestMapper.toResponse(
                serviceAreaServicePort.create(new CreateServiceAreaCommand(request.nombre(), idSede)));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Obtener un area por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/service-areas/{id}")
    public ServiceAreaResponse getById(@PathVariable UUID id) {
        return clientRestMapper.toResponse(serviceAreaServicePort.findById(id));
    }

    @Operation(
            summary = "Cambiar el nombre de un area",
            description = "La sede no se puede cambiar: un area no se traslada.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/service-areas/{id}")
    public ServiceAreaResponse renameServiceArea(
            @PathVariable UUID id, @Valid @RequestBody ServiceAreaCreateRequest request) {

        return clientRestMapper.toResponse(
                serviceAreaServicePort.rename(new RenameServiceAreaCommand(id, request.nombre())));
    }

    @Operation(summary = "Cerrar un area de servicio", description = "No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/service-areas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateServiceArea(@PathVariable UUID id) {
        serviceAreaServicePort.deactivate(new DeactivateServiceAreaCommand(id));
    }
}
