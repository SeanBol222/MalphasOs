package com.malphasos.malphasos.client.infrastructure.input.rest;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.services.client.commands.AddClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AddClientPhoneCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AppointRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.CreateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.DeactivateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveClientPhoneCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.UpdateClientCommand;
import com.malphasos.malphasos.client.infrastructure.input.mapper.ClientRestMapper;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ClientCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ClientUpdateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ContactRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.response.ClientResponse;
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
 * API de clientes, con sus contactos y sus representantes legales.
 *
 * <p>Los contactos y los representantes cuelgan de la ruta del cliente porque no existen sin él. En
 * el original los correos del cliente colgaban de rutas propias y se creaban con {@code PUT}, como
 * si tuvieran vida aparte.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/clients")
@Tag(name = "Client", description = "Gestion de clientes")
public class ClientRestAdapter {

    private final ClientServicePort clientServicePort;
    private final ClientRestMapper clientRestMapper;

    @Operation(summary = "Listar todos los clientes")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping
    public List<ClientResponse> getAllClients() {
        return clientRestMapper.toClientResponseList(clientServicePort.findAll());
    }

    @Operation(summary = "Obtener un cliente por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/{id}")
    public ClientResponse getClientById(
            @Parameter(description = "Identificador del cliente") @PathVariable UUID id) {

        return clientRestMapper.toResponse(clientServicePort.findById(id));
    }

    @Operation(summary = "Registrar un cliente")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody ClientCreateRequest request) {

        ClientResponse creado = clientRestMapper.toResponse(
                clientServicePort.create(new CreateClientCommand(
                        request.documento(),
                        request.tipoIdentificacion(),
                        request.razonSocial(),
                        request.idPais())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(
            summary = "Cambiar los datos de un cliente",
            description = "Los campos ausentes conservan su valor. El documento no se puede cambiar.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/{id}")
    public ClientResponse updateClient(
            @PathVariable UUID id, @Valid @RequestBody ClientUpdateRequest request) {

        return clientRestMapper.toResponse(clientServicePort.update(
                new UpdateClientCommand(id, request.razonSocial(), request.idPais())));
    }

    @Operation(summary = "Retirar un cliente", description = "No lo borra: lo deja inactivo.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateClient(@PathVariable UUID id) {
        clientServicePort.deactivate(new DeactivateClientCommand(id));
    }

    @Operation(summary = "Agregar un correo de contacto")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/{id}/emails")
    public ResponseEntity<ClientResponse> addEmail(
            @PathVariable UUID id, @Valid @RequestBody ContactRequest request) {

        ClientResponse actualizado = clientRestMapper.toResponse(
                clientServicePort.addEmail(new AddClientEmailCommand(id, request.valor())));

        return ResponseEntity.status(HttpStatus.CREATED).body(actualizado);
    }

    @Operation(summary = "Retirar un correo", description = "Lo deja inactivo, no lo borra.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}/emails/{idCorreo}")
    public ClientResponse removeEmail(@PathVariable UUID id, @PathVariable UUID idCorreo) {
        return clientRestMapper.toResponse(
                clientServicePort.removeEmail(new RemoveClientEmailCommand(id, idCorreo)));
    }

    @Operation(summary = "Agregar un telefono de contacto")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/{id}/phones")
    public ResponseEntity<ClientResponse> addPhone(
            @PathVariable UUID id, @Valid @RequestBody ContactRequest request) {

        ClientResponse actualizado = clientRestMapper.toResponse(
                clientServicePort.addPhone(new AddClientPhoneCommand(id, request.valor())));

        return ResponseEntity.status(HttpStatus.CREATED).body(actualizado);
    }

    @Operation(summary = "Retirar un telefono", description = "Lo deja inactivo, no lo borra.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}/phones/{idTelefono}")
    public ClientResponse removePhone(@PathVariable UUID id, @PathVariable UUID idTelefono) {
        return clientRestMapper.toResponse(
                clientServicePort.removePhone(new RemoveClientPhoneCommand(id, idTelefono)));
    }

    @Operation(
            summary = "Nombrar representante legal",
            description = "La persona debe existir. Una persona puede representar a varios clientes.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/{id}/representatives/{idPersona}")
    public ResponseEntity<ClientResponse> appointRepresentative(
            @PathVariable UUID id, @PathVariable UUID idPersona) {

        ClientResponse actualizado = clientRestMapper.toResponse(
                clientServicePort.appointRepresentative(
                        new AppointRepresentativeCommand(id, idPersona)));

        return ResponseEntity.status(HttpStatus.CREATED).body(actualizado);
    }

    @Operation(summary = "Retirar a un representante legal")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}/representatives/{idPersona}")
    public ClientResponse removeRepresentative(
            @PathVariable UUID id, @PathVariable UUID idPersona) {

        return clientRestMapper.toResponse(
                clientServicePort.removeRepresentative(
                        new RemoveRepresentativeCommand(id, idPersona)));
    }
}
