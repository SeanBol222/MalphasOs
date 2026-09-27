package com.malphasos.malphasos.report.infrastructure.input.rest;

import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.OpenServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.VerificationReadingCommand;
import com.malphasos.malphasos.report.infrastructure.input.mapper.ServiceReportRestMapper;
import com.malphasos.malphasos.report.infrastructure.input.model.request.RecordVerificationRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.ServiceReportFillRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.ServiceReportOpenRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ServiceReportResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de los reportes de servicio.
 *
 * <p><b>El listado exige un filtro</b>, a diferencia del de órdenes de trabajo. Un reporte solo tiene
 * sentido dentro de su orden o dentro del historial de su equipo; «todos los reportes del sistema» no
 * responde a ninguna pregunta del dominio, así que la operación que lo devolvería no existe.
 *
 * <p><b>Cada cosa que le pasa a un reporte tiene su ruta</b>: llenarlo, registrar su verificación,
 * cerrarlo y retirarlo son hechos distintos, con su evento y su significado. Cerrar no es un campo
 * que se edita, es una operación — la misma decisión que en las órdenes de trabajo, donde el estado
 * se avanza y no se escribe.
 *
 * <p>Dos autoridades: consultar es {@code report.read} y todo lo demás {@code report.write}. No hay
 * una tercera para cerrar, y conviene decir por qué: quien llena el reporte es quien lo firma en
 * campo, de modo que separarlas describiría un reparto de trabajo que en esta empresa no existe. La
 * firma digital (RF-21) sí traerá la suya cuando llegue.
 *
 * <p>Este grupo de OpenAPI —{@code reports}— apuntaba a un módulo que no existía desde que se
 * declaró: {@code pathsToMatch} no casaba con ninguna ruta y el grupo salía vacío sin avisar. Su
 * prueba de cobertura entra con estas rutas, en el mismo commit, que es lo que la convención del
 * proyecto pide.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/reports")
@Tag(name = "ServiceReport", description = "Lo que se hizo sobre cada equipo de una orden")
public class ServiceReportRestAdapter {

    private final ServiceReportServicePort serviceReportServicePort;
    private final ServiceReportRestMapper mapper;

    @Operation(summary = "Listar reportes de una orden o de un equipo",
            description = "Hay que indicar uno de los dos filtros, y solo uno: un reporte no se "
                    + "consulta suelto, sino dentro de su orden o del historial de su equipo.")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping
    public List<ServiceReportResponse> getAll(
            @Parameter(description = "Los reportes de esta orden de trabajo")
            @RequestParam(required = false) UUID idOrdenTrabajo,
            @Parameter(description = "El historial de reportes de este equipo")
            @RequestParam(required = false) UUID idEquipoCliente) {

        if ((idOrdenTrabajo == null) == (idEquipoCliente == null)) {
            throw new IllegalArgumentException(
                    "Hay que filtrar por orden de trabajo o por equipo, y solo por uno de los dos");
        }

        return idOrdenTrabajo != null
                ? mapper.toList(serviceReportServicePort.findByWorkOrder(idOrdenTrabajo))
                : mapper.toList(serviceReportServicePort.findByEquipment(idEquipoCliente));
    }

    @Operation(summary = "Obtener un reporte por su identificador")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping("/{id}")
    public ServiceReportResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(serviceReportServicePort.findById(id));
    }

    @Operation(summary = "Abrir el reporte de un equipo de una orden",
            description = "Nace en borrador y vacio. La orden debe haber empezado y el equipo "
                    + "seguir en su alcance; un equipo no tiene dos reportes vivos en la misma orden.")
    @PreAuthorize("hasAuthority('report.write')")
    @PostMapping("/work-orders/{idOrdenTrabajo}")
    public ResponseEntity<ServiceReportResponse> open(
            @PathVariable UUID idOrdenTrabajo,
            @Valid @RequestBody ServiceReportOpenRequest request) {

        ServiceReportResponse abierto = mapper.toResponse(serviceReportServicePort.open(
                new OpenServiceReportCommand(idOrdenTrabajo, request.idEquipoCliente())));

        return ResponseEntity.status(HttpStatus.CREATED).body(abierto);
    }

    @Operation(summary = "Registrar la informacion tecnica (RF-15)",
            description = "Un campo ausente deja el valor como estaba y uno en blanco lo borra. "
                    + "Un reporte cerrado ya no se llena.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}")
    public ServiceReportResponse fill(
            @PathVariable UUID id, @Valid @RequestBody ServiceReportFillRequest request) {

        return mapper.toResponse(serviceReportServicePort.fill(new FillServiceReportCommand(
                id,
                request.fallaReportada(),
                request.diagnostico(),
                request.procedimientos(),
                request.observaciones(),
                request.resultado())));
    }

    @Operation(summary = "Registrar la verificacion metrologica",
            description = "Sustituye la verificacion anterior entera; las lecturas corregidas "
                    + "quedan retiradas, no borradas. Cada lectura se comprueba contra los puntos "
                    + "y la cantidad de datos que declara el tipo del equipo.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/verification")
    public ServiceReportResponse recordVerification(
            @PathVariable UUID id, @Valid @RequestBody RecordVerificationRequest request) {

        List<VerificationReadingCommand> lecturas = request.lecturas().stream()
                .map(lectura -> new VerificationReadingCommand(
                        lectura.idPuntoVerificacion(),
                        lectura.secuencia(),
                        lectura.valorPatron(),
                        lectura.valorEquipo(),
                        lectura.unidadSinPunto()))
                .toList();

        return mapper.toResponse(serviceReportServicePort.recordVerification(
                new RecordVerificationCommand(id, lecturas)));
    }

    @Operation(summary = "Cerrar el reporte",
            description = "Exige procedimientos y resultado, y la verificacion completa si el tipo "
                    + "del equipo se verifica. Un equipo que queda fuera de servicio se cierra sin "
                    + "lecturas: no se le puede medir nada. Desde aqui el reporte ya no cambia.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/finish")
    public ServiceReportResponse finish(@PathVariable UUID id) {
        return mapper.toResponse(
                serviceReportServicePort.finish(new FinishServiceReportCommand(id)));
    }

    @Operation(summary = "Retirar un reporte",
            description = "No lo borra: lo deja inactivo. Es la unica forma de corregir uno ya "
                    + "cerrado, retirandolo y abriendo otro.")
    @PreAuthorize("hasAuthority('report.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID id) {
        serviceReportServicePort.discard(new DiscardServiceReportCommand(id));
    }
}
