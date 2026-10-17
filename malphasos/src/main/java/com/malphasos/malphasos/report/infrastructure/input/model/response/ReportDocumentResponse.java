package com.malphasos.malphasos.report.infrastructure.input.model.response;

import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que el informe imprime y no es del reporte (2026-10-17): congelado al cerrar, o compuesto en vivo
 * si el reporte es borrador. El reporte mismo se pide aparte, por su ruta.
 */
@Schema(name = "ReportDocumentResponse")
public record ReportDocumentResponse(
        @Schema(description = "Si es la copia hecha al cerrar; falso en un borrador, que se compone en vivo")
        boolean congelado,
        @Schema(description = "Si se congelo despues del cierre, con los datos de ese dia: reportes cerrados"
                + " antes de que el informe se congelara")
        boolean tardio,
        Orden orden,
        Cliente cliente,
        Equipo equipo,
        @Schema(description = "Las verificaciones del tipo del equipo, en su orden") List<Verificacion> verificaciones,
        @Schema(description = "Nula si no habia empresa configurada") Empresa empresa,
        @Schema(description = "Nombre completo del ingeniero de la orden; nulo si no tenia") String ingeniero,
        @Schema(description = "La firma guardada del ingeniero, PNG como data: URI; nula si no tenia")
        String firmaIngeniero,
        @Schema(description = "La firma de quien recibe, PNG como data: URI; nula mientras no firme. Desde V39")
        String firmaRecibe) {

    @Schema(name = "ReportDocumentOrden")
    public record Orden(
            String numero, String tipoServicio, boolean garantia, String periodicidad, LocalDate fechaProgramada) {
    }

    @Schema(name = "ReportDocumentCliente")
    public record Cliente(
            String nombre,
            String documento,
            String sede,
            String direccionSede,
            String ciudadSede,
            String area,
            List<String> responsables,
            List<String> telefonos,
            List<String> correos) {
    }

    @Schema(name = "ReportDocumentEquipo")
    public record Equipo(
            String tipo,
            String marca,
            String modelo,
            String serie,
            String numeroHojaVida,
            String numeroInventario,
            String codigoInterno,
            String tecnologia,
            String uso,
            @Schema(description = "Clase de riesgo: I, IIA, IIB o III; nula si no se sabe") String riesgo,
            String alimentacion,
            Integer voltaje,
            Integer potencia,
            BigDecimal amperaje,
            Integer frecuencia) {
    }

    @Schema(name = "ReportDocumentVerificacion")
    public record Verificacion(
            UUID id,
            String magnitud,
            String unidad,
            @Schema(description = "Lecturas por punto; nula si la modalidad no las fija") Integer cantidadDatos,
            List<Punto> puntos) {
    }

    @Schema(name = "ReportDocumentPunto")
    public record Punto(UUID id, BigDecimal valor) {
    }

    @Schema(name = "ReportDocumentEmpresa")
    public record Empresa(
            String nombre,
            String nit,
            String direccion,
            String ciudad,
            String movil,
            String garantia,
            String aclaracion,
            String codigoInforme,
            @Schema(description = "El logo como data: URI; nulo si no lleva") String logo) {
    }

    public static ReportDocumentResponse from(ReportDocumentView vista) {
        ReportDocument documento = vista.documento();
        ReportDocument.Orden orden = documento.orden();
        ReportDocument.Cliente cliente = documento.cliente();
        ReportDocument.Equipo equipo = documento.equipo();
        ReportDocument.Empresa empresa = documento.empresa();

        return new ReportDocumentResponse(
                vista.congelado(),
                vista.reporte().isDocumentoTardio(),
                new Orden(orden.numero(), orden.tipoServicio(), orden.garantia(), orden.periodicidad(),
                        orden.fechaProgramada()),
                new Cliente(cliente.nombre(), cliente.documento(), cliente.sede(), cliente.direccionSede(),
                        cliente.ciudadSede(), cliente.area(), cliente.responsables(), cliente.telefonos(),
                        cliente.correos()),
                new Equipo(equipo.tipo(), equipo.marca(), equipo.modelo(), equipo.serie(), equipo.numeroHojaVida(),
                        equipo.numeroInventario(), equipo.codigoInterno(), equipo.tecnologia(), equipo.uso(),
                        equipo.riesgo(), equipo.alimentacion(), equipo.voltaje(), equipo.potencia(),
                        equipo.amperaje(), equipo.frecuencia()),
                documento.verificaciones().stream()
                        .map(v -> new Verificacion(v.id(), v.magnitud(), v.unidad(), v.cantidadDatos(),
                                v.puntos().stream().map(p -> new Punto(p.id(), p.valor())).toList()))
                        .toList(),
                empresa == null ? null : new Empresa(empresa.nombre(), empresa.nit(), empresa.direccion(),
                        empresa.ciudad(), empresa.movil(), empresa.garantia(), empresa.aclaracion(),
                        empresa.codigoInforme(), vista.logo() == null ? null : vista.logo().dataUri()),
                documento.ingeniero(),
                vista.firmaIngeniero() == null ? null : vista.firmaIngeniero().dataUri(),
                vista.firmaRecibe() == null ? null : vista.firmaRecibe().dataUri());
    }
}
