package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import java.math.BigDecimal;

/**
 * Un punto de verificación tal como llega desde fuera: solo su valor.
 *
 * <p><b>Ya no trae la unidad</b> (2026-10-03): la declara su verificación una sola vez y el punto la
 * hereda. Antes había que repetirla en cada punto, con el agujero de que dos puntos hermanos podían
 * contradecirse.
 *
 * <p>Sin identificador, y eso es deliberado: los puntos <b>no se editan uno a uno</b>. Declarar qué se
 * verifica en un tipo es una sola decisión, así que se manda la lista entera y el agregado retira los
 * anteriores. Con identificadores por punto habría que responder qué significa recibir uno que no está
 * en la lista, y esa pregunta no tiene una respuesta mejor que «se retira».
 */
public record VerificationPointCommand(BigDecimal valor) {
}
