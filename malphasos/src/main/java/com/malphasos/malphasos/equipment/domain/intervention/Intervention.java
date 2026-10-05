package com.malphasos.malphasos.equipment.domain.intervention;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un mantenimiento, una calibración o un diagnóstico que ya se le hizo a un equipo instalado: una
 * línea del historial de su hoja de vida.
 *
 * <p><b>Es un `record` y no un agregado de Generación 2, y conviene decir por qué.</b> Un agregado
 * existe para proteger invariantes a lo largo de los cambios que sufre, y una intervención <b>no
 * cambia</b>: describe algo que pasó. No tiene métodos que digan qué ocurrió porque ella misma <b>es</b>
 * lo que ocurrió. Darle la maquinaria de eventos habría añadido un mecanismo que nadie usa —este
 * sistema ya tiene siete eventos sin un solo consumidor— para un tipo que no tiene comportamiento.
 *
 * <p><b>Sus tres datos son una copia congelada</b> del reporte en el momento de cerrarse, no una
 * referencia a lo que el reporte diga hoy. Guarda {@code idReporteServicio} para poder volver al
 * reporte completo desde el historial, y eso es trazabilidad, no dependencia: si el reporte se
 * corrige después, o se retira, esta línea no cambia. Es la misma decisión que el área congelada de
 * {@code orden_trabajo_equipo}, y el esquema lleva escrita la foránea que por eso no existe.
 */
public record Intervention(
        UUID id,
        UUID idEquipoCliente,
        UUID idReporteServicio,
        LocalDateTime fechaServicio,
        InterventionType tipoServicio,
        InterventionResult resultado,
        boolean estadoActivo,
        UUID reemplazadaPor) {

    public Intervention {
        exigir(id, "identificador");
        exigir(idEquipoCliente, "el equipo al que se le hizo");
        exigir(idReporteServicio, "el reporte del que sale");
        exigir(fechaServicio, "la fecha en que se hizo");
        exigir(tipoServicio, "el tipo de servicio");
        exigir(resultado, "el resultado");

        if (reemplazadaPor != null && estadoActivo) {
            throw new IllegalArgumentException(
                    "Una intervencion reemplazada no puede seguir activa: contaria dos veces el mismo "
                            + "mantenimiento");
        }
    }

    /**
     * Registra una intervención a partir de un reporte que acaba de cerrarse.
     *
     * <p>No comprueba que el reporte esté finalizado ni que exista: eso lo sabe quien la invoca —el
     * oyente del cierre— y lo garantiza el esquema con sus dos foráneas. Lo que este tipo garantiza
     * es que no se registre una línea de historial a la que le falte alguno de los datos que RF-27
     * exige.
     */
    public static Intervention record(
            UUID idEquipoCliente,
            UUID idReporteServicio,
            LocalDateTime fechaServicio,
            InterventionType tipoServicio,
            InterventionResult resultado) {

        return new Intervention(
                UUID.randomUUID(),
                idEquipoCliente,
                idReporteServicio,
                fechaServicio,
                tipoServicio,
                resultado,
                true,
                null);
    }

    /**
     * Esta misma intervención, reemplazada por la del reporte que la corrigió.
     *
     * <p>Es lo único que le puede pasar a una intervención después de registrarse, y no es un cambio
     * de lo que describe: el mantenimiento sigue habiendo ocurrido como dice. Lo que cambia es que
     * <b>deja de contar</b> en la hoja de vida, porque otra línea describe ya el mismo servicio, y
     * queda apuntando a ella para que el rastro de la corrección no se pierda.
     *
     * <p>Devuelve una copia —es un {@code record}— y es idempotente: reemplazar dos veces por la misma
     * sustituta da lo mismo.
     */
    public Intervention reemplazadaPor(UUID idSustituta) {
        exigir(idSustituta, "la intervencion que la sustituye");

        if (idSustituta.equals(id)) {
            throw new IllegalArgumentException("Una intervencion no se reemplaza a si misma");
        }

        return new Intervention(
                id, idEquipoCliente, idReporteServicio, fechaServicio, tipoServicio, resultado,
                false, idSustituta);
    }

    /**
     * Reconstruye una intervención leída del almacén.
     *
     * <p>Como en los agregados del proyecto, leer de la base no es un hecho del dominio: este camino
     * no valida nada que el constructor compacto no exija ya, y existe para que el nombre diga de
     * dónde viene el objeto.
     */
    public static Intervention rehydrate(
            UUID id,
            UUID idEquipoCliente,
            UUID idReporteServicio,
            LocalDateTime fechaServicio,
            InterventionType tipoServicio,
            InterventionResult resultado,
            boolean estadoActivo,
            UUID reemplazadaPor) {

        return new Intervention(
                id, idEquipoCliente, idReporteServicio, fechaServicio, tipoServicio, resultado,
                estadoActivo, reemplazadaPor);
    }

    /**
     * Lanza {@code IllegalArgumentException} y no una excepción propia a propósito: el advice de este
     * módulo ya la traduce a {@code ERR_EQUIPMENT_007}, «datos de equipo inválidos», que es lo que
     * esto es. Una excepción nueva habría obligado a un código de error nuevo para decir lo mismo.
     */
    private static void exigir(Object valor, String que) {
        if (valor == null) {
            throw new IllegalArgumentException("Una intervencion necesita " + que);
        }
    }
}
