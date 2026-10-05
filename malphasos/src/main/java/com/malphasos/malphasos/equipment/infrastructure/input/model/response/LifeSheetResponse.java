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
        List<InterventionResponse> servicioTecnico,
        @Schema(description = "La empresa que presta el servicio: el membrete del documento") Empresa empresa) {

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
            boolean estadoActivo,
            @Schema(description = "Encargados del area, o de la sede si el area no tiene; vacia si ninguno")
            List<String> responsables,
            @Schema(description = "Telefonos vigentes del cliente") List<String> telefonosCliente,
            @Schema(description = "Correos vigentes del cliente") List<String> correosCliente) {
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

    @Schema(name = "LifeSheetEmpresa", description = "Viene de la configuracion, no de la base")
    public record Empresa(
            String nombre,
            String direccion,
            String ciudad,
            @Schema(description = "Telefonos fijos; vacia si no tiene") List<String> telefonos,
            String movil,
            String correo) {
    }

    @Schema(name = "LifeSheetFabricante")
    public record Fabricante(String nombre, @Schema(description = "Opcional") String pais) {
    }
}
