package com.malphasos.malphasos.workorder.domain.workOrder;

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
                exigir(idEquipoCliente, "unidad"), exigir(idAreaServicio, "area de servicio"));
    }

    /**
     * Rechaza el nulo como dato inválido y no como fallo de programación.
     *
     * <p>{@code Objects.requireNonNull} sería lo natural si el nulo delatara un error de quien
     * escribió el código, pero aquí llega de fuera: alguien envía una petición sin el identificador
     * del equipo. La diferencia se paga en el API — cada módulo traduce
     * {@code IllegalArgumentException} a un 400 en su advice, y nadie traduce
     * {@code NullPointerException}, que saldría como 500 fuera del contrato de error.
     */
    private static UUID exigir(UUID valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Un equipo de la orden necesita su " + campo);
        }

        return valor;
    }
}
