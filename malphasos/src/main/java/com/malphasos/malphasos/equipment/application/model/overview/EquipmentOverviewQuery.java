package com.malphasos.malphasos.equipment.application.model.overview;

import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que se le pide al listado de equipos: qué buscar, por qué filtrar y qué página.
 *
 * <p>Los filtros nulos no filtran. La página empieza en cero y su tamaño se acota a [1, 100]: un
 * tamaño absurdo no es un error de quien pregunta que valga la pena devolverle, es una página más
 * corta o más larga.
 *
 * @param texto        se busca en la serie, el número de hoja de vida, el modelo, el tipo y la marca,
 *                     sin distinguir mayúsculas ni tildes
 * @param resultado    el del último servicio cerrado del equipo
 * @param dadosDeBaja  {@code false} lista los equipos en servicio; {@code true}, los dados de baja
 * @param ids          solo estos equipos, <b>estén como estén</b>: con identificadores no se filtra por
 *                     {@code dadosDeBaja}, porque quien los pide —la ficha de una orden— necesita también
 *                     el que se dio de baja después de entrar en ella. Vacío o nulo no filtra
 */
public record EquipmentOverviewQuery(
        String texto,
        UUID idCliente,
        UUID idTipoEquipo,
        InterventionResult resultado,
        boolean dadosDeBaja,
        Set<UUID> ids,
        int pagina,
        int tamano) {

    public static final int TAMANO_POR_DEFECTO = 25;
    public static final int TAMANO_MAXIMO = 100;

    public EquipmentOverviewQuery {
        texto = texto == null || texto.isBlank() ? null : texto.strip();
        ids = ids == null || ids.isEmpty() ? null : Set.copyOf(ids);
        pagina = Math.max(pagina, 0);
        tamano = Math.clamp(tamano, 1, TAMANO_MAXIMO);
    }

    /** La consulta de siempre, sin identificadores: es la del listado. */
    public EquipmentOverviewQuery(
            String texto, UUID idCliente, UUID idTipoEquipo, InterventionResult resultado,
            boolean dadosDeBaja, int pagina, int tamano) {
        this(texto, idCliente, idTipoEquipo, resultado, dadosDeBaja, null, pagina, tamano);
    }
}
