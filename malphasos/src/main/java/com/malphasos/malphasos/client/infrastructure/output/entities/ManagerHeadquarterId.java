package com.malphasos.malphasos.client.infrastructure.output.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Llave compuesta de {@code encargado_sede}: la persona y la sede (2026-10-27, `V41`). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManagerHeadquarterId implements Serializable {

    private UUID persona;
    private UUID sede;

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ManagerHeadquarterId id)) {
            return false;
        }

        return Objects.equals(persona, id.persona) && Objects.equals(sede, id.sede);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persona, sede);
    }
}
