package com.malphasos.malphasos.client.infrastructure.output.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Llave compuesta de {@code representante_legal}.
 *
 * <p>La relación entre persona y cliente es de muchos a muchos —una persona puede representar a
 * varios clientes—, de modo que la llave primaria son las dos columnas. Se distingue así de
 * {@code encargado}, cuya llave es la de la persona sola.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LegalRepresentativeId implements Serializable {

    private UUID persona;
    private UUID cliente;

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof LegalRepresentativeId id)) {
            return false;
        }

        return Objects.equals(persona, id.persona) && Objects.equals(cliente, id.cliente);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persona, cliente);
    }
}
