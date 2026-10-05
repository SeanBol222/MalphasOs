package com.malphasos.malphasos.client.application.ports.input;

import java.util.List;
import java.util.UUID;

/**
 * Lo tercero que este módulo publica hacia los demás: quién responde por un área de servicio.
 *
 * <p>Existe para la hoja de vida de un equipo, que imprime un «profesional responsable». La regla
 * que lo decide es de este módulo, porque los encargados son suyos: <b>los encargados del área, y si
 * el área no tiene ninguno, los de su sede</b> —decidido por el usuario el 2026-10-05—. Quien
 * pregunta solo recibe nombres; no necesita saber qué es un encargado ni que hay dos niveles.
 *
 * <p>Es el mismo patrón que {@link ClientOwnershipPort}: una superficie mínima hacia un módulo que ya
 * dependía de este.
 */
public interface ServiceAreaResponsiblePort {

    /**
     * Los nombres completos de quienes responden por el área, en orden alfabético.
     *
     * <p>Una lista y no un nombre porque nada impide que un área tenga dos encargados, y escoger uno
     * sería decidir algo que nadie decidió. Vacía si ni el área ni su sede tienen encargado activo.
     * Las personas dadas de baja no cuentan, aunque sigan figurando como encargadas.
     */
    List<String> responsiblesFor(UUID idAreaServicio);
}
