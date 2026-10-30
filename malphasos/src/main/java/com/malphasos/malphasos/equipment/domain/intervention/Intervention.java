package com.malphasos.malphasos.equipment.domain.intervention;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un mantenimiento, una calibración o un diagnóstico que ya se le hizo a un equipo instalado: una
 * línea del historial de su hoja de vida.
 *
 * <p><b>Desde {@code V52} (2026-10-28) no se guarda: se deriva de un reporte cerrado.</b> Decidido por el
 * usuario con el análisis del esquema: todo lo que la línea dice —fecha, tipo de servicio, resultado, qué se
 * hizo y quién— lo dice ya el reporte, y la tabla {@code intervencion} que lo copiaba desde {@code V12} era
 * una segunda fuente del mismo hecho. La línea la compone {@code report}, que es quien tiene los reportes,
 * por {@code ServiceHistoryPort}, que declara este módulo.
 *
 * <p><b>La regla de {@code V14} pasa de dato a filtro.</b> Un reporte cerrado no se edita: se sustituye, y el
 * sustituto, al cerrarse, reemplaza a la línea del sustituido. Hasta {@code V52} eso se guardaba en
 * {@code k_id_reemplazada_por}; desde entonces es que cada par (orden, equipo) da una sola línea, la de su
 * último reporte cerrado. Mientras el sustituto no se cierra, cuenta el sustituido, porque el mantenimiento
 * sí ocurrió.
 *
 * <p>Es un {@code record} sin comportamiento: describe algo que pasó.
 *
 * @param idReporteServicio el reporte del que sale, que es también lo que la identifica
 * @param descripcion lo que se hizo: los pasos hechos y el trabajo adicional, como lo escribe el reporte
 * @param responsable el ingeniero de la orden, como lo congeló el documento del reporte al cerrarse
 */
public record Intervention(
        UUID idEquipoCliente,
        UUID idReporteServicio,
        LocalDateTime fechaServicio,
        InterventionType tipoServicio,
        InterventionResult resultado,
        String descripcion,
        String responsable) {

    public Intervention {
        exigir(idEquipoCliente, "el equipo al que se le hizo");
        exigir(idReporteServicio, "el reporte del que sale");
        exigir(fechaServicio, "la fecha en que se hizo");
        exigir(tipoServicio, "el tipo de servicio");
        exigir(resultado, "el resultado");
    }

    /** Con lo que la identifica, que desde {@code V52} es su reporte. */
    public UUID id() {
        return idReporteServicio;
    }

    private static void exigir(Object valor, String que) {
        if (valor == null) {
            throw new IllegalArgumentException("Una intervencion necesita " + que);
        }
    }
}
