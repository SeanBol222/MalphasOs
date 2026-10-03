package com.malphasos.malphasos.equipment.domain.magnitude;

import java.util.UUID;

/**
 * Qué se mide en una verificación metrológica: temperatura, presión, humedad relativa.
 *
 * <p><b>No es un agregado, y la diferencia con {@code Country} es deliberada.</b> Un país se puede
 * renombrar y retirar desde el API porque el sistema original ya administraba países, de modo que
 * {@code Country} nació con sus tres eventos y su servicio de escritura. Una magnitud no: entra
 * sembrada por {@code V10}, no hay pantalla ni operación que la cree, y construirle
 * {@code create/rename/deactivate} con sus eventos sería maquinaria sin un solo llamante. Este
 * proyecto ya tiene la regla escrita —no se reserva nada para lo que no existe, porque un patrón
 * reservado es indistinguible de uno roto—, así que es un dato de referencia que se lee.
 *
 * <p>Si algún día hay que administrarlas, convertirlo en agregado es un cambio acotado: lo que no se
 * puede deshacer es haber construido eventos que nadie emite.
 *
 * @param codigo la llave natural, estable y sin acentos, como el ISO de un país
 */
public record Magnitude(UUID id, String codigo, String nombre, boolean estadoActivo) {

    public Magnitude {
        if (id == null) {
            throw new IllegalArgumentException("Una magnitud necesita su identificador");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Una magnitud necesita su codigo");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una magnitud necesita su nombre");
        }
    }
}
