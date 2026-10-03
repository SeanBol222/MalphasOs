package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.MetrologyCatalogServicePort;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.MagnitudeResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.MeasurementUnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API del catálogo metrológico: qué se puede medir y en qué unidades.
 *
 * <p><b>Solo lectura, y no es un recorte.</b> El catálogo entra sembrado por {@code V10} y no hay
 * pantalla que lo administre, igual que los 249 países y las 1.350 ciudades de {@code V7}: son datos de
 * referencia que una instalación nueva necesita el primer día. Añadir rutas de escritura que ninguna
 * pantalla usa sería reservar un patrón para lo que no existe, y eso ya tiene su regla en este
 * proyecto. Administrarlo desde la aplicación queda anotado como deuda.
 *
 * <p><b>Con la autoridad de equipos y no con una propia</b>, y es deliberado: estas dos listas solo
 * sirven para declarar cómo se verifica un tipo de equipo, de modo que quien puede leer el catálogo de
 * equipos puede leer esto. Inventar {@code magnitude.read} obligaría a tocar el realm, los tres grupos y
 * la expansión del administrador para separar algo que nadie va a separar.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/magnitudes")
@Tag(name = "MetrologyCatalog", description = "Magnitudes y unidades con las que se verifica")
public class MetrologyCatalogRestAdapter {

    private final MetrologyCatalogServicePort metrologyCatalogServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar las magnitudes que se pueden verificar")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<MagnitudeResponse> getAll() {
        return mapper.toMagnitudeList(metrologyCatalogServicePort.findAllMagnitudes());
    }

    @Operation(summary = "Listar las unidades de una magnitud",
            description = "Responde 404 si la magnitud no existe: una lista vacia seria"
                    + " indistinguible de una magnitud sin unidades.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{magnitudId}/units")
    public List<MeasurementUnitResponse> getUnits(@PathVariable UUID magnitudId) {
        return mapper.toUnitList(metrologyCatalogServicePort.findUnitsByMagnitude(magnitudId));
    }
}
