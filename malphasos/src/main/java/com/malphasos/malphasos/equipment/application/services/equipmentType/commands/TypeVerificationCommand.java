package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import java.util.List;
import java.util.UUID;

/**
 * Una de las cosas que se verifican en un tipo de equipo, tal como llega desde fuera.
 *
 * <p>La magnitud y la unidad llegan <b>por identificador</b>, como toda referencia que cruza un
 * agregado. Que la unidad pertenezca a la magnitud lo comprueba el servicio antes de guardar, para que
 * el llamante reciba «esa unidad no es de esa magnitud» en vez de un conflicto de integridad genérico
 * — aunque el esquema lo garantice también con una foránea compuesta.
 *
 * <p>Sin identificador propio, por lo mismo que {@link VerificationPointCommand}: se manda la lista
 * entera y las anteriores se retiran.
 */
public record TypeVerificationCommand(
        UUID magnitudId,
        UUID unidadId,
        VerificationMode modalidad,
        Integer cantidadDatos,
        List<VerificationPointCommand> puntos) {
}
