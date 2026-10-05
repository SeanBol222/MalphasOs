package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La hoja de vida de un equipo instalado, con sus cuatro secciones.
 *
 * <p>Las cuatro son las que enumera el criterio de aceptación de RF-22, con esos nombres, para que el
 * documento y el requisito se puedan leer uno contra el otro. El historial va dentro y no aparte: es
 * la cuarta sección, no un documento distinto.
 */
@Schema(name = "LifeSheetResponse", description = "Hoja de vida de un equipo instalado")
public record LifeSheetResponse(
        @Schema(description = "Que unidad es y de quien") Identificacion identificacion,
        @Schema(description = "Que es el equipo: tipo, marca y modelo") Tecnica tecnica,
        @Schema(description = "Quien lo fabrico") Fabricante fabricante,
        @Schema(description = "Historial de intervenciones, de la mas reciente a la mas antigua")
        List<InterventionResponse> servicioTecnico) {

    @Schema(name = "LifeSheetIdentificacion")
    public record Identificacion(
            UUID idEquipoCliente,
            String serie,
            String numeroInventario,
            LocalDate fechaCompra,
            Long valorCompra,
            String cliente,
            String documentoCliente,
            String sede,
            String direccionSede,
            String ciudadSede,
            String areaServicio,
            boolean estadoActivo) {
    }

    @Schema(name = "LifeSheetTecnica")
    public record Tecnica(
            String tipoEquipo,
            String definicionTecnica,
            String tecnologiaPredominante,
            String recomendacionesCuidado,
            Integer voltaje,
            BigDecimal amperaje,
            String marca,
            String modelo,
            @Schema(description = "Registro INVIMA del modelo, opcional") String registroInvima) {
    }

    @Schema(name = "LifeSheetFabricante")
    public record Fabricante(String nombre, @Schema(description = "Opcional") String pais) {
    }
}
