package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import java.math.BigDecimal;

/**
 * Un punto de verificación tal como llega desde fuera: su valor y su unidad.
 *
 * <p>Sin identificador, y eso es deliberado: los puntos <b>no se editan uno a uno</b>. Declarar cómo se
 * verifica un tipo es una sola decisión —modalidad, cuántas lecturas y en qué valores—, así que se manda
 * la lista entera y el agregado retira los anteriores. Con identificadores por punto habría que
 * responder qué significa recibir uno que no está en la lista, y esa pregunta no tiene una respuesta
 * mejor que «se retira».
 */
public record VerificationPointCommand(BigDecimal valor, String unidad) {
}
