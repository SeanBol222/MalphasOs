package com.malphasos.malphasos.workorder.domain.workOrderStandards;

import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.events.WorkOrderStandardsAssignedEvent;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.events.WorkOrderStandardsAssignedPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Los patrones que una orden lleva a su visita para un tipo de equipo (V60, 2026-11-19).
 *
 * <p>Decidido por el usuario: la orden elige los suyos <b>por tipo</b>, y no una lista para toda la visita,
 * porque así reparte dos patrones del mismo uso —las pesas de 5 kg y las de 20 kg— entre la pesa bebé y la
 * de piso de la misma orden. Cada reporte de ese tipo los recibe en las verificaciones que el catálogo les
 * permite.
 *
 * <p>Su identidad es el par orden y tipo, en una sola {@link Clave} —el contrato de identidad pide un solo
 * campo incluido—: hay uno por tipo con equipos en la orden, aunque esté vacío. Es un
 * conjunto, sin orden y sin borrado lógico: lo que se usó queda en el documento de cada reporte cerrado y en
 * el de la orden ejecutada.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkOrderStandards extends AggregateRoot {

    /** La identidad: la orden y el tipo para el que lleva los patrones. */
    public record Clave(UUID idOrdenTrabajo, UUID idTipoEquipo) {

        public Clave {
            if (idOrdenTrabajo == null || idTipoEquipo == null) {
                throw new IllegalArgumentException(
                        "Los patrones de una orden son de una orden y de un tipo de equipo");
            }
        }
    }

    @EqualsAndHashCode.Include
    private final Clave clave;

    private final Set<UUID> patrones = new HashSet<>();

    private WorkOrderStandards(UUID idOrdenTrabajo, UUID idTipoEquipo, Iterable<UUID> patrones) {
        this.clave = new Clave(idOrdenTrabajo, idTipoEquipo);
        patrones.forEach(this.patrones::add);
    }

    public UUID getIdOrdenTrabajo() {
        return clave.idOrdenTrabajo();
    }

    public UUID getIdTipoEquipo() {
        return clave.idTipoEquipo();
    }

    /** Los de un tipo que todavía no lleva ninguno. */
    public static WorkOrderStandards empty(UUID idOrdenTrabajo, UUID idTipoEquipo) {
        return new WorkOrderStandards(idOrdenTrabajo, idTipoEquipo, List.of());
    }

    /** Leer de la base no es un hecho del dominio: no emite. */
    public static WorkOrderStandards rehydrate(UUID idOrdenTrabajo, UUID idTipoEquipo, Iterable<UUID> patrones) {
        return new WorkOrderStandards(idOrdenTrabajo, idTipoEquipo, patrones);
    }

    /** Copia inmutable. */
    public Set<UUID> getPatrones() {
        return Set.copyOf(patrones);
    }

    /** Si la orden lleva este patrón para este tipo. */
    public boolean lleva(UUID idPatron) {
        return patrones.contains(idPatron);
    }

    /**
     * Fija los patrones del tipo en la orden. Una lista vacía los quita todos.
     *
     * <p>Un patrón no se repite en la lista: dos veces el mismo es un error de quien la arma. Si lo pedido
     * es lo que ya había, no emite nada.
     */
    public void assign(List<UUID> pedidos) {
        if (pedidos == null) {
            throw new IllegalArgumentException("La lista de patrones puede estar vacía, pero no faltar");
        }

        Set<UUID> nuevos = new HashSet<>();

        for (UUID pedido : pedidos) {
            if (pedido == null) {
                throw new IllegalArgumentException("Cada patrón de la lista necesita su identificador");
            }
            if (!nuevos.add(pedido)) {
                throw new IllegalArgumentException("El patrón " + pedido + " está dos veces en la lista");
            }
        }

        if (nuevos.equals(patrones)) {
            return;
        }

        patrones.clear();
        patrones.addAll(nuevos);
        registerEvent(new WorkOrderStandardsAssignedEvent(
                metadataFor(WorkOrderStandardsAssignedEvent.TYPE),
                new WorkOrderStandardsAssignedPayload(getIdOrdenTrabajo(), getIdTipoEquipo(), new ArrayList<>(pedidos))));
    }

    @Override
    protected String aggregateType() {
        return "WorkOrderStandards";
    }

    @Override
    protected String aggregateId() {
        return getIdOrdenTrabajo() + "/" + getIdTipoEquipo();
    }
}
