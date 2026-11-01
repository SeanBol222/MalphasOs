package com.malphasos.malphasos.client.infrastructure.output.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Llave compuesta de {@code supervisor_cliente}, que era {@code representante_legal} hasta V53.
 *
 * <p>La relación entre persona y cliente es de muchos a muchos —una persona puede supervisar a
 * varios clientes—, de modo que la llave primaria son las dos columnas.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientSupervisorId implements Serializable {

    private UUID persona;
    private UUID cliente;

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ClientSupervisorId id)) {
            return false;
        }

        return Objects.equals(persona, id.persona) && Objects.equals(cliente, id.cliente);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persona, cliente);
    }
}
