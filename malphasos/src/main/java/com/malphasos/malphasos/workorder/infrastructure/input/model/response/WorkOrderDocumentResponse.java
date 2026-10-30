package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lo que la orden era al ejecutarse (V49): desde entonces la ficha se lee de aquí y no en vivo. */
@Schema(name = "WorkOrderDocumentResponse")
public record WorkOrderDocumentResponse(
        int version,
        String numero,
        String tipoServicio,
        boolean garantia,
        String periodicidad,
        LocalDate fechaMantenimiento,
        @Schema(description = "Nula si la orden se congelo despues de ejecutarse") LocalDate fechaEjecucion,
        Cliente cliente,
        Sede sede,
        String ingeniero,
        List<Equipo> equipos) {

    @Schema(name = "WorkOrderDocumentClient")
    public record Cliente(String nombre, String documento) {
    }

    @Schema(name = "WorkOrderDocumentSite")
    public record Sede(String nombre, String direccion, String ciudad) {
    }

    @Schema(name = "WorkOrderDocumentEquipment")
    public record Equipo(
            UUID id,
            String area,
            String tipo,
            String marca,
            String modelo,
            String serie,
            String numeroHojaVida,
            @Schema(description = "El numero de su reporte vigente al ejecutarse; nulo si no tenia") String numeroReporte) {
    }

    public static WorkOrderDocumentResponse from(WorkOrderDocument documento) {
        return new WorkOrderDocumentResponse(
                documento.version(),
                documento.numero(),
                documento.tipoServicio(),
                documento.garantia(),
                documento.periodicidad(),
                documento.fechaMantenimiento(),
                documento.fechaEjecucion(),
                new Cliente(documento.cliente().nombre(), documento.cliente().documento()),
                new Sede(documento.sede().nombre(), documento.sede().direccion(), documento.sede().ciudad()),
                documento.ingeniero(),
                documento.equipos().stream()
                        .map(e -> new Equipo(e.id(), e.area(), e.tipo(), e.marca(), e.modelo(), e.serie(),
                                e.numeroHojaVida(), e.numeroReporte()))
                        .toList());
    }
}
