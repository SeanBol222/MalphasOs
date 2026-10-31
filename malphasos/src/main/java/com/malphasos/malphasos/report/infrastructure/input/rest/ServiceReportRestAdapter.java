package com.malphasos.malphasos.report.infrastructure.input.rest;

import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.MarkReportStepCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordHandoverCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordIntakeCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReplaceServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RemoveReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.UseReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.VerificationReadingCommand;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.input.mapper.ServiceReportRestMapper;
import com.malphasos.malphasos.report.infrastructure.input.model.request.MarkReportStepRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.RecordHandoverRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.RecordIntakeRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.RecordVerificationRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.ServiceReportFillRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.SignReceiptRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.UseReferenceStandardRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ReportDocumentResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ServiceReportResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.WorkOrderReportProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * firma de quien recibe (V39) tampoco trae la suya: firma en la pantalla de quien lleva el reporte.
 * (Aquí decía que RF-21 «traerá la suya cuando llegue»; corregido el 2026-10-17, al construirla.)
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

    /** Lo que produce el lienzo de firma: un PNG como data: URI. */
    private static final String PREFIJO_PNG = "data:image/png;base64,";

    /** Traduce quién llama a un alcance de lectura. Ver {@code ClientRestAdapter}. */
    private final ReadScopeResolver readScopeResolver;

    /** Un reporte con lo que dice de sus pasos, verificaciones y patrones sin guardarlo (V51). */
    private ServiceReportResponse responder(ServiceReport reporte) {
        return mapper.toResponse(reporte, serviceReportServicePort.catalogOf(reporte));
    }

    private List<ServiceReportResponse> responder(List<ServiceReport> reportes) {
        return reportes.stream().map(this::responder).toList();
    }

    @Operation(summary = "Listar reportes de una orden o de un equipo",
            description = "Hay que indicar uno de los dos filtros, y solo uno: un reporte no se "
                    + "consulta suelto, sino dentro de su orden o del historial de su equipo.")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping
    public List<ServiceReportResponse> getAll(
            @Parameter(description = "Los reportes de esta orden de trabajo")
            @RequestParam(required = false) UUID idOrdenTrabajo,
            @Parameter(description = "El historial de reportes de este equipo")
            @RequestParam(required = false) UUID idEquipoCliente,
            Authentication autenticacion) {

        if ((idOrdenTrabajo == null) == (idEquipoCliente == null)) {
            throw new IllegalArgumentException(
                    "Hay que filtrar por orden de trabajo o por equipo, y solo por uno de los dos");
        }

        ReadScope alcance = readScopeResolver.scopeFor(autenticacion);

        return idOrdenTrabajo != null
                ? responder(serviceReportServicePort.findByWorkOrder(idOrdenTrabajo, alcance))
                : responder(serviceReportServicePort.findByEquipment(idEquipoCliente, alcance));
    }

    @Operation(summary = "Avance de los reportes por orden",
            description = "Para cada orden con algun reporte vivo, cuantos equipos lo tienen cerrado y"
                    + " cuantos en borrador. Una orden sin reportes no aparece. Acotado al alcance de"
                    + " quien llama.")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping("/progress")
    public List<WorkOrderReportProgressResponse> progress(Authentication autenticacion) {
        return serviceReportServicePort.progressByWorkOrder(readScopeResolver.scopeFor(autenticacion)).stream()
                .map(fila -> new WorkOrderReportProgressResponse(
                        fila.idOrdenTrabajo(), fila.cerrados(), fila.abiertos()))
                .toList();
    }

    @Operation(summary = "Obtener un reporte por su identificador")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping("/{id}")
    public ServiceReportResponse getById(@PathVariable UUID id, Authentication autenticacion) {
        return responder(
                serviceReportServicePort.findById(id, readScopeResolver.scopeFor(autenticacion)));
    }

    @Operation(summary = "El informe de un reporte",
            description = "Lo que imprime de la orden, el cliente, el equipo y la empresa: congelado al cerrar,"
                    + " compuesto en vivo si es borrador. Con el mismo alcance que el reporte.")
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping("/{id}/document")
    public ReportDocumentResponse getDocument(@PathVariable UUID id, Authentication autenticacion) {
        return ReportDocumentResponse.from(
                serviceReportServicePort.document(id, readScopeResolver.scopeFor(autenticacion)));
    }

    @Operation(summary = "El informe de un reporte en PDF",
            description = "RF-17: el mismo informe, compuesto en el servidor y convertido por Gotenberg. Un"
                    + " borrador sale marcado como tal. Con el mismo alcance que el reporte.")
    @ApiResponse(responseCode = "200", description = "El PDF",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @PreAuthorize("hasAuthority('report.read')")
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getPdf(@PathVariable UUID id, Authentication autenticacion) {
        ReportPdf pdf = serviceReportServicePort.pdf(id, readScopeResolver.scopeFor(autenticacion));

        // En línea y no como adjunto: la pantalla lo muestra, y su botón lo descarga con este nombre.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.nombre()).build().toString())
                .body(pdf.contenido());
    }

    @Operation(summary = "Registrar la informacion tecnica (RF-15)",
            description = "Un campo ausente deja el valor como estaba y uno en blanco lo borra. "
                    + "Un reporte cerrado ya no se llena.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}")
    public ServiceReportResponse fill(
            @PathVariable UUID id, @Valid @RequestBody ServiceReportFillRequest request) {

        return responder(serviceReportServicePort.fill(new FillServiceReportCommand(
                id,
                request.fallaReportada(),
                request.diagnostico(),
                request.procedimientos(),
                request.observaciones(),
                request.causa(),
                request.repuestos(),
                request.resultado())));
    }

    @Operation(summary = "Registrar como llego el equipo",
            description = "Las dos verificaciones al ingreso -estructura fisica y estado operativo- y sus "
                    + "accesorios, que sustituyen a los que hubiera. Una no conforme exige observacion. Solo en "
                    + "un borrador.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/intake")
    public ServiceReportResponse recordIntake(
            @PathVariable UUID id, @Valid @RequestBody RecordIntakeRequest request) {

        return responder(serviceReportServicePort.recordIntake(new RecordIntakeCommand(
                id,
                request.fisico(),
                request.observacionFisico(),
                request.operativo(),
                request.observacionOperativo(),
                request.accesorios() == null
                        ? List.of()
                        : request.accesorios().stream()
                                .map(a -> new RecordIntakeCommand.Accessory(a.nombre(), a.conforme(), a.observacion()))
                                .toList())));
    }

    @Operation(summary = "Registrar quien recibe el equipo",
            description = "Nombre, cedula y cargo, en texto: quien recibe no tiene por que estar registrado. "
                    + "Los tres en blanco lo borran. Solo en un borrador.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/handover")
    public ServiceReportResponse recordHandover(
            @PathVariable UUID id, @Valid @RequestBody RecordHandoverRequest request) {

        return responder(serviceReportServicePort.recordHandover(
                new RecordHandoverCommand(id, request.nombre(), request.cedula(), request.cargo())));
    }

    @Operation(summary = "Firmar el recibido",
            description = "Quien recibe el servicio firma en la pantalla (RF-18), antes o despues de cerrar: es "
                    + "lo unico que se anade a un reporte cerrado. Una vez firmado no cambia. Con alcance AREA u "
                    + "ORDEN la misma firma va a los reportes activos del area o de la orden que aun no tengan "
                    + "(RF-21). Devuelve "
                    + "los reportes firmados, el pedido primero.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/receipt-signature")
    public List<ServiceReportResponse> signReceipt(
            @PathVariable UUID id, @Valid @RequestBody SignReceiptRequest request) {

        if (!request.imagen().startsWith(PREFIJO_PNG)) {
            throw new IllegalArgumentException("La firma tiene que llegar como " + PREFIJO_PNG + "...");
        }
        byte[] firma;
        try {
            firma = Base64.getDecoder().decode(request.imagen().substring(PREFIJO_PNG.length()));
        } catch (IllegalArgumentException base64Roto) {
            throw new IllegalArgumentException("La firma no es un base64 valido");
        }

        return responder(serviceReportServicePort.signReceipt(new SignReceiptCommand(
                id, request.nombre(), request.cedula(), request.cargo(), firma, request.alcance())));
    }

    @Operation(summary = "Registrar la verificacion metrologica",
            description = "Sustituye la verificacion anterior entera; las lecturas corregidas "
                    + "quedan retiradas, no borradas. Cada lectura declara a que verificacion del "
                    + "tipo pertenece, y se comprueba contra los puntos y la cantidad de datos que "
                    + "esa verificacion declara.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/verification")
    public ServiceReportResponse recordVerification(
            @PathVariable UUID id, @Valid @RequestBody RecordVerificationRequest request) {

        List<VerificationReadingCommand> lecturas = request.lecturas().stream()
                .map(lectura -> new VerificationReadingCommand(
                        lectura.idVerificacion(),
                        lectura.idPuntoVerificacion(),
                        lectura.secuencia(),
                        lectura.valorPatron(),
                        lectura.valorEquipo()))
                .toList();

        return responder(serviceReportServicePort.recordVerification(
                new RecordVerificationCommand(id, lecturas)));
    }

    /**
     * Pone un patrón en una verificación del reporte (V27; por verificación desde V30). El reporte copia
     * lo que el catálogo dice de él hoy; si su certificado está vencido el día del cierre, el reporte no
     * se cierra.
     */
    @Operation(summary = "Poner un patron en una verificacion",
            description = "Copia lo que el catalogo dice hoy del patron, ficha incluida. Ponerlo otra vez "
                    + "refresca la copia. La verificacion tiene que ser una activa del tipo del equipo, y el "
                    + "patron no puede estar retirado. Solo en un borrador.")
    @PreAuthorize("hasAuthority('report.write')")
    @PostMapping("/{id}/verifications/{idVerificacion}/reference-standards")
    public ServiceReportResponse useReferenceStandard(
            @PathVariable UUID id,
            @PathVariable UUID idVerificacion,
            @Valid @RequestBody UseReferenceStandardRequest request) {

        return responder(serviceReportServicePort.useReferenceStandard(
                new UseReferenceStandardCommand(id, idVerificacion, request.idEquipoPatron())));
    }

    @Operation(summary = "Quitar un patron de una verificacion",
            description = "Lo retira, no lo borra. Quitar uno que no esta no hace nada. Solo en un borrador.")
    @PreAuthorize("hasAuthority('report.write')")
    @DeleteMapping("/{id}/verifications/{idVerificacion}/reference-standards/{idEquipoPatron}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReferenceStandard(
            @PathVariable UUID id, @PathVariable UUID idVerificacion, @PathVariable UUID idEquipoPatron) {

        serviceReportServicePort.removeReferenceStandard(
                new RemoveReferenceStandardCommand(id, idVerificacion, idEquipoPatron));
    }

    @Operation(summary = "Marcar un paso del protocolo",
            description = "HECHO, NO_APLICA -con nota obligatoria- o PENDIENTE otra vez. El paso se nombra por "
                    + "su protocolo, que un reporte no repite. Solo en un borrador.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/steps/{idProtocolo}")
    public ServiceReportResponse markStep(
            @PathVariable UUID id, @PathVariable UUID idProtocolo, @Valid @RequestBody MarkReportStepRequest request) {

        return responder(serviceReportServicePort.markStep(
                new MarkReportStepCommand(id, idProtocolo, request.estado(), request.nota())));
    }

    @Operation(summary = "Cerrar el reporte",
            description = "Exige resultado; todos los pasos del protocolo marcados si los tiene, o "
                    + "procedimientos si no; y la verificacion completa si el tipo "
                    + "del equipo se verifica. Un equipo que queda fuera de servicio se cierra sin "
                    + "lecturas: no se le puede medir nada. Desde aqui el reporte ya no cambia.")
    @PreAuthorize("hasAuthority('report.write')")
    @PatchMapping("/{id}/finish")
    public ServiceReportResponse finish(@PathVariable UUID id) {
        return responder(
                serviceReportServicePort.finish(new FinishServiceReportCommand(id)));
    }

    @Operation(summary = "Sustituir un reporte cerrado",
            description = "Lo retira y abre el borrador del mismo equipo en la misma orden, en un solo "
                    + "paso. Es la unica forma de corregir uno cerrado, tambien con la orden ejecutada.")
    @PreAuthorize("hasAuthority('report.write')")
    @PostMapping("/{id}/replace")
    public ResponseEntity<ServiceReportResponse> replace(@PathVariable UUID id) {
        ServiceReportResponse borrador =
                responder(serviceReportServicePort.replace(new ReplaceServiceReportCommand(id)));

        return ResponseEntity.status(HttpStatus.CREATED).body(borrador);
    }

    @Operation(summary = "Retirar un borrador",
            description = "No lo borra: lo deja inactivo, y con el su equipo sale de la orden. Un reporte "
                    + "cerrado no se retira: se sustituye.")
    @PreAuthorize("hasAuthority('report.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID id) {
        serviceReportServicePort.discard(new DiscardServiceReportCommand(id));
    }
}
