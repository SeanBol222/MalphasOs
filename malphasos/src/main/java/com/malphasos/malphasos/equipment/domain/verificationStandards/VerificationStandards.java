package com.malphasos.malphasos.equipment.domain.verificationStandards;

import com.malphasos.malphasos.equipment.domain.verificationStandards.events.VerificationStandardsAssignedEvent;
import com.malphasos.malphasos.equipment.domain.verificationStandards.events.VerificationStandardsAssignedPayload;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Los equipos patrón con que se hace una verificación de un tipo de equipo (V60, 2026-11-19).
 *
 * <p>Decidido por el usuario: el ingeniero no elige entre todos los patrones de la empresa en cada
 * verificación, sino entre los que el catálogo dice que sirven para ella —medir presión con un simulador
 * de SpO2 es un error que hoy cabe—.
 *
 * <p><b>Es un agregado propio y no una colección más de la verificación</b>, por lo mismo que los
 * protocolos de un tipo: cambia por motivos distintos —se compra un patrón, se retira otro— y no necesita
 * al tipo para validarse. Su identidad es la de la verificación: hay uno por verificación, aunque esté
 * vacío.
 *
 * <p>No tiene orden ni borrado lógico: es un conjunto. Lo que el reporte usó queda en su documento.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VerificationStandards extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID idVerificacion;

    private final Set<UUID> patrones = new HashSet<>();

    private VerificationStandards(UUID idVerificacion, Iterable<UUID> patrones) {
        if (idVerificacion == null) {
            throw new IllegalArgumentException("Los patrones de una verificación son de una verificación");
        }

        this.idVerificacion = idVerificacion;
        patrones.forEach(this.patrones::add);
    }

    /** Los de una verificación que todavía no tiene ninguno. */
    public static VerificationStandards empty(UUID idVerificacion) {
        return new VerificationStandards(idVerificacion, List.of());
    }

    /** Leer de la base no es un hecho del dominio: no emite. */
    public static VerificationStandards rehydrate(UUID idVerificacion, Iterable<UUID> patrones) {
        return new VerificationStandards(idVerificacion, patrones);
    }

    /** Copia inmutable. */
    public Set<UUID> getPatrones() {
        return Set.copyOf(patrones);
    }

    /** Si este patrón sirve para esta verificación. */
    public boolean admite(UUID idPatron) {
        return patrones.contains(idPatron);
    }

    /**
     * Fija los patrones de la verificación. Una lista vacía los quita todos.
     *
     * <p>Un patrón no se repite en la lista: dos veces el mismo es un error de quien la arma, no algo que
     * convenga callar. Si lo pedido es lo que ya había, no emite nada.
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
        registerEvent(new VerificationStandardsAssignedEvent(
                metadataFor(VerificationStandardsAssignedEvent.TYPE),
                new VerificationStandardsAssignedPayload(idVerificacion, new ArrayList<>(pedidos))));
    }

    @Override
    protected String aggregateType() {
        return "VerificationStandards";
    }

    @Override
    protected String aggregateId() {
        return idVerificacion.toString();
    }
}
