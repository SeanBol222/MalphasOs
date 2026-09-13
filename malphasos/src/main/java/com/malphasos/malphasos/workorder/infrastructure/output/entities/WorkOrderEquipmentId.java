package com.malphasos.malphasos.workorder.infrastructure.output.entities;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Llave compuesta de la tabla puente: el mismo equipo no se lista dos veces en la misma orden.
 *
 * <p>Los nombres de los campos han de coincidir con los de la entidad que la usa por
 * {@code @IdClass}; el de la orden es la relación, y JPA toma de ella su identificador.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WorkOrderEquipmentId implements Serializable {

    private UUID orden;

    private UUID equipoCliente;
}
