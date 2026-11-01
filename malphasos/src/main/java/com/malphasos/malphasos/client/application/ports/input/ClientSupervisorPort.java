package com.malphasos.malphasos.client.application.ports.input;

import java.util.List;
import java.util.UUID;

/**
 * Lo tercero que este módulo publica hacia los demás: quién supervisa a un cliente (V53, 2026-11-01).
 *
 * <p>Existe para los documentos —la hoja de vida y el informe imprimen quién responde por el equipo—.
 * Sustituye a la regla de los encargados, «los del área, y si no, los de la sede», que salió con ellos:
 * ahora responde quien supervisa al cliente, en todas sus sedes. Quien pregunta solo recibe nombres.
 *
 * <p>Es el mismo patrón que {@link ClientOwnershipPort}: una superficie mínima hacia un módulo que ya
 * dependía de este.
 */
public interface ClientSupervisorPort {

    /**
     * Los nombres completos de quienes supervisan al cliente, en orden alfabético.
     *
     * <p>Una lista y no un nombre porque un cliente puede tener dos supervisores, y escoger uno sería
     * decidir algo que nadie decidió. Vacía si no tiene ninguno activo. Las personas dadas de baja no
     * cuentan, aunque sigan nombradas.
     */
    List<String> supervisorNamesOf(UUID idCliente);
}
