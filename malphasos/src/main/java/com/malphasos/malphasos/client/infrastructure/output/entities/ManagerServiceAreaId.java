package com.malphasos.malphasos.client.infrastructure.output.entities;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Llave compuesta de {@code encargado_area}: la persona y el área (2026-10-27, `V41`). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManagerServiceAreaId implements Serializable {

    private UUID persona;
    private UUID area;

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof ManagerServiceAreaId id)) {
            return false;
        }

        return Objects.equals(persona, id.persona) && Objects.equals(area, id.area);
    }

    @Override
    public int hashCode() {
        return Objects.hash(persona, area);
    }
}
