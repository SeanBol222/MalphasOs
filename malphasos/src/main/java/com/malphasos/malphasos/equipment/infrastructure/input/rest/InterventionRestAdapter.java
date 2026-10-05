package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.InterventionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El historial de intervenciones de un equipo: la cuarta sección de su hoja de vida.
 *
 * <p><b>Solo lectura, y es la decisión.</b> No hay operación de alta porque el segundo criterio de
 * RF-26 dice que «no se requiere acción manual para actualizar el historial»: la única forma de que
 * aparezca una línea es que un reporte de servicio se cierre. Y no hay operación de edición ni de
 * baja porque una intervención describe algo que pasó, y eso no se corrige: lo que se corrige es el
 * reporte, y hacerlo <b>no reescribe el historial</b> a propósito.
 *
 * <p>Cuelga de la ruta del equipo porque un historial no existe sin su equipo, igual que los contactos
 * cuelgan de su cliente.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
@Tag(name = "Intervention", description = "Historial de intervenciones de un equipo")
public class InterventionRestAdapter {

    private final InterventionServicePort interventionServicePort;

    /** Traduce quién llama a un alcance de lectura. Ver {@code ClientRestAdapter}. */
    private final ReadScopeResolver readScopeResolver;

    @Operation(
            summary = "Historial de intervenciones de un equipo",
            description = "De la mas reciente a la mas antigua. Se alimenta solo al cerrar un reporte.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/client-equipments/{id}/interventions")
    public List<InterventionResponse> getByEquipment(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            Authentication autenticacion) {

        return interventionServicePort.findByEquipment(id, readScopeResolver.scopeFor(autenticacion)).stream()
                .map(InterventionRestAdapter::toResponse)
                .toList();
    }

    private static InterventionResponse toResponse(Intervention intervencion) {
        return new InterventionResponse(
                intervencion.id(),
                intervencion.idEquipoCliente(),
                intervencion.idReporteServicio(),
                intervencion.fechaServicio(),
                intervencion.tipoServicio().name(),
                intervencion.resultado().name(),
                intervencion.descripcion(),
                intervencion.responsable());
    }
}
