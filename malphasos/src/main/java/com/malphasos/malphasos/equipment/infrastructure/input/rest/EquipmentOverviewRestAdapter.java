package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.client.application.model.ServiceAreaLocation;
import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentOverviewServicePort;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El listado de equipos instalados, paginado y filtrado en el servidor (2026-10-11).
 *
 * <p>Ruta aparte de {@code GET /client-equipments}, que se queda como está: devuelve las unidades
 * tal cual y la usan pantallas que necesitan la lista entera, como el alta de una orden.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
@Tag(name = "EquipmentOverview", description = "Listado de equipos instalados")
public class EquipmentOverviewRestAdapter {

    private final EquipmentOverviewServicePort equipmentOverviewServicePort;
    private final ReadScopeResolver readScopeResolver;

    @Operation(summary = "Listar los equipos instalados, con su ubicacion y su ultimo servicio",
            description = "Busca en serie, numero de hoja de vida, modelo, tipo y marca sin distinguir "
                    + "mayusculas ni tildes. Los filtros ausentes no filtran.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/client-equipments/overview")
    public EquipmentOverviewResponse list(
            @Parameter(description = "Texto a buscar") @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID idCliente,
            @RequestParam(required = false) UUID idTipoEquipo,
            @Parameter(description = "Resultado del ultimo servicio") @RequestParam(required = false) InterventionResult resultado,
            @Parameter(description = "true para listar los dados de baja") @RequestParam(defaultValue = "false") boolean dadosDeBaja,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + EquipmentOverviewQuery.TAMANO_POR_DEFECTO) int tamano,
            Authentication autenticacion) {

        EquipmentOverview listado = equipmentOverviewServicePort.list(
                new EquipmentOverviewQuery(q, idCliente, idTipoEquipo, resultado, dadosDeBaja, pagina, tamano),
                readScopeResolver.scopeFor(autenticacion));

        return new EquipmentOverviewResponse(
                listado.filas().stream().map(EquipmentOverviewRestAdapter::fila).toList(),
                listado.total(),
                listado.pagina(),
                listado.tamano());
    }

    private static EquipmentOverviewResponse.Fila fila(EquipmentOverview.Row fila) {
        Optional<ServiceAreaLocation> donde = Optional.ofNullable(fila.ubicacion());

        return new EquipmentOverviewResponse.Fila(
                fila.id(),
                fila.numeroHojaVida(),
                fila.serie(),
                fila.idTipoEquipo(),
                fila.tipoEquipo(),
                fila.marca(),
                fila.modelo(),
                fila.estadoActivo(),
                fila.idAreaServicio(),
                donde.map(ServiceAreaLocation::area).orElse(null),
                donde.map(ServiceAreaLocation::idSede).orElse(null),
                donde.map(ServiceAreaLocation::sede).orElse(null),
                donde.map(ServiceAreaLocation::idCliente).orElse(null),
                donde.map(ServiceAreaLocation::cliente).orElse(null),
                fila.ultimoServicio(),
                fila.resultado());
    }
}
