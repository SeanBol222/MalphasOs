package com.malphasos.malphasos.equipment.application.model.lifeSheet;

import java.util.List;
import java.util.UUID;

/**
 * La hoja de vida tal como se imprime (2026-10-18): cada dato ya escrito como sale en el papel —fechas,
 * etiquetas, rayas donde falta algo—, para que la plantilla solo lo coloque. La compone
 * {@code LifeSheetPrintComposer} con las mismas reglas que tenía la pantalla cuando la hoja se imprimía
 * desde el navegador.
 */
public record LifeSheetPrint(
        UUID idEquipoCliente,
        Empresa empresa,
        String numeroHojaVida,
        String nombreDelEquipo,
        String serie,
        String area,
        String cliente,
        boolean deBaja,
        Estado estado,
        Identificacion identificacion,
        Cliente datosDelCliente,
        String definicion,
        /** La foto del equipo como {@code data:} URI (V58), o nula: el recuadro sale vacío. */
        String foto,
        String notaDeCompra,
        Caracteristicas caracteristicas,
        List<Paso> protocolo,
        List<String> cuidado,
        String limpiezaCotidiana,
        List<Linea> historial,
        String codigoYFecha) {

    /** El membrete. Sin empresa configurada, todo vacío: el documento sale sin él. */
    public record Empresa(String nombre, String logo, String direccion, String telefonos, String correo) {

        public static final Empresa NINGUNA = new Empresa("", null, "", "", "");
    }

    /** La fila de estado: lo que se sabe del equipo en tres segundos. */
    public record Estado(String actual, String ultimoServicio, int intervenciones, String riesgo) {
    }

    public record Identificacion(
            String tipo, String marcaYModelo, String serie, String placaYCodigo, String invima, String area) {
    }

    public record Cliente(
            String razonSocialYNit, String sedeYCiudad, String direccion, String responsable, String telefonos,
            String correos) {
    }

    public record Caracteristicas(
            String tecnologia, String uso, String alimentacion, String frecuencia, String voltaje, String potencia,
            String corriente, String especificas) {
    }

    /** Un paso del protocolo preventivo, en el orden de su prioridad. */
    public record Paso(String nombre, String descripcion, String indicacion) {
    }

    /**
     * Una intervención del historial.
     *
     * @param marca la clave del resultado, que decide la forma de su cuadrado
     * @param enRojo si el resultado no es «operativo»
     */
    public record Linea(
            String fecha, String servicio, String descripcion, String responsable, String marca, String resultado,
            boolean enRojo) {
    }
}
