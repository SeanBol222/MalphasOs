package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
        List<HistoryLineResponse> servicioTecnico,
        @Schema(description = "La empresa que presta el servicio: el membrete del documento. Nula si no se ha configurado")
                Empresa empresa) {

    @Schema(name = "LifeSheetIdentificacion")
    public record Identificacion(
            UUID idEquipoCliente,
            @Schema(description = "HV-<sigla>-0001, asignado al registrar el equipo", example = "HV-CDN-0001")
            String numeroHojaVida,
            String serie,
            String numeroInventario,
            @Schema(description = "Opcional") String codigoInterno,
            @Schema(description = "Quien vendio esta maquina, opcional") String proveedor,
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
            @Schema(description = "Correos vigentes del cliente") List<String> correosCliente,
            @Schema(description = "La foto del equipo, PNG como data: URI; nula si no tiene") String foto) {
    }

    @Schema(name = "LifeSheetTecnica")
    public record Tecnica(
            String tipoEquipo,
            String definicionTecnica,
            String tecnologiaPredominante,
            String recomendacionesCuidado,
            @Schema(description = "Del tipo de equipo, opcional") String uso,
            @Schema(description = "Del tipo de equipo, opcional") String limpiezaCotidiana,
            String marca,
            String modelo,
            @Schema(description = "Registro INVIMA del modelo, opcional") String registroInvima,
            @Schema(description = "Del modelo: riesgo, caracteristicas y datos electricos")
            TechnicalSheetResponse fichaTecnica,
            @Schema(description = "Los protocolos del preventivo del tipo, en orden de prioridad. Vacia si no tiene")
            List<PasoDelProtocolo> protocoloPreventivo) {
    }

    @Schema(name = "LifeSheetPasoDelProtocolo")
    public record PasoDelProtocolo(
            int prioridad,
            String nombre,
            String descripcion,
            @Schema(description = "Lo propio de este tipo de equipo. Nula si no hay") String indicacion) {
    }

    @Schema(name = "LifeSheetEmpresa", description = "Viene de la configuracion, no de la base ni del codigo")
    public record Empresa(
            String nombre,
            @Schema(description = "Opcional") String nit,
            String direccion,
            String ciudad,
            @Schema(description = "Telefonos fijos; vacia si no tiene") List<String> telefonos,
            String movil,
            String correo,
            @Schema(description = "El logo como data: URI; nulo si no hay") String logo,
            @Schema(description = "El texto de garantia del informe; nulo si no hay") String garantia,
            @Schema(description = "La aclaracion del proceso del informe; nula si no hay") String aclaracion,
            @Schema(description = "Codigo del formato de la hoja de vida; nulo si no hay") String codigoHojaDeVida,
            @Schema(description = "Codigo del formato del informe; nulo si no hay") String codigoInforme) {
    }

    @Schema(name = "LifeSheetFabricante")
    public record Fabricante(String nombre, @Schema(description = "Opcional") String pais) {
    }
}
