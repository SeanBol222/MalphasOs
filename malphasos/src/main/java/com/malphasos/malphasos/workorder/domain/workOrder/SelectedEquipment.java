package com.malphasos.malphasos.workorder.domain.workOrder;

import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Un equipo incluido en una orden, con el área en la que estaba <b>al seleccionarlo</b>.
 *
 * <p>El área se copia y no se consulta. Un equipo puede trasladarse después a otra área de la misma
 * sede, y si esta pieza guardara una referencia viva, el registro de una orden ya ejecutada pasaría
 * a señalar un sitio donde el servicio nunca se prestó. Congelarla es lo que permite que el
 * historial siga diciendo la verdad. Ver la misma decisión en {@code V6__work_order.sql}, que por
 * eso tampoco pone una clave foránea compuesta contra la unidad.
 *
 * <p><b>Su identidad es el equipo, no el par.</b> Dentro de una orden el mismo equipo no aparece dos
 * veces —lo impide también la llave primaria de {@code orden_trabajo_equipo}—, de modo que añadirlo
 * de nuevo con otra área no crea una segunda entrada: no hace nada.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SelectedEquipment {

    @EqualsAndHashCode.Include
    private final UUID idEquipoCliente;

    private final UUID idAreaServicio;

    private SelectedEquipment(UUID idEquipoCliente, UUID idAreaServicio) {
        this.idEquipoCliente = idEquipoCliente;
        this.idAreaServicio = idAreaServicio;
    }

    public static SelectedEquipment of(UUID idEquipoCliente, UUID idAreaServicio) {
        return new SelectedEquipment(
                Objects.requireNonNull(idEquipoCliente, "Un equipo de la orden necesita su unidad"),
                Objects.requireNonNull(idAreaServicio, "Un equipo de la orden necesita su area"));
    }
}
