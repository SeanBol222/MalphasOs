package com.malphasos.malphasos.equipment.application.model.overview;

import com.malphasos.malphasos.client.application.model.ServiceAreaLocation;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una página del listado de equipos, ya compilada: lo que la pantalla pinta sin cruzar nada.
 *
 * <p>Existe desde el 2026-10-11, con el rediseño. Hasta entonces la pantalla pedía la lista de
 * unidades y la cruzaba con cuatro listas del catálogo y una petición por área, y aun así no podía
 * decir cuándo fue el último servicio ni cómo quedó el equipo: eso vive en el historial de cada uno.
 * Es un modelo de lectura, como {@code LifeSheet}: no es un agregado y nadie lo guarda.
 *
 * @param total cuántos equipos cumplen los filtros, no cuántos trae esta página
 */
public record EquipmentOverview(List<Row> filas, long total, int pagina, int tamano) {

    public EquipmentOverview {
        filas = List.copyOf(filas);
    }

    public static EquipmentOverview empty(EquipmentOverviewQuery consulta) {
        return new EquipmentOverview(List.of(), 0, consulta.pagina(), consulta.tamano());
    }

    /**
     * Un equipo instalado, con lo que lo identifica y cómo quedó en su último servicio.
     *
     * @param ultimoServicio la fecha de la intervención vigente más reciente; nula si nunca se le hizo
     *                       un servicio cerrado
     * @param ubicacion      cliente, sede y área; la pone el servicio, no la consulta, porque son de
     *                       otro módulo
     */
    public record Row(
            UUID id,
            String numeroHojaVida,
            String serie,
            UUID idTipoEquipo,
            String tipoEquipo,
            String marca,
            String modelo,
            boolean estadoActivo,
            UUID idAreaServicio,
            LocalDateTime ultimoServicio,
            InterventionResult resultado,
            ServiceAreaLocation ubicacion) {

        public Row conUbicacion(ServiceAreaLocation dondeEsta) {
            return new Row(id, numeroHojaVida, serie, idTipoEquipo, tipoEquipo, marca, modelo,
                    estadoActivo, idAreaServicio, ultimoServicio, resultado, dondeEsta);
        }
    }
}
