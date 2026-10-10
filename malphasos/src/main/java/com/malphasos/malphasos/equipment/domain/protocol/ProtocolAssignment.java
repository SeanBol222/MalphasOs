package com.malphasos.malphasos.equipment.domain.protocol;

import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolsAssignedEvent;
import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolsAssignedPayload;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Los protocolos de mantenimiento preventivo de un tipo de equipo, en el orden en que se hacen.
 *
 * <p><b>Es un agregado propio y no una colección más de {@code EquipmentType}</b>: el tipo ya lleva sus
 * verificaciones, y su reconstrucción la usan medio módulo; esto cambia por motivos distintos —se
 * reordena, se le quita una actividad— y no necesita al tipo para validarse. Su identidad es la del tipo
 * al que pertenece: hay una asignación por tipo, aunque esté vacía.
 *
 * <p><b>La prioridad la decide el usuario y la deriva este agregado de la posición</b>, decidido el
 * 2026-10-10: se recibe la lista en el orden en que se quiere, y el primero es la prioridad 1. Así no
 * puede llegar un hueco ni una prioridad repetida, que es la regla —van seguidas— y que el esquema solo
 * puede expresar a medias.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProtocolAssignment extends AggregateRoot {

    static final int LARGO_INDICACION = 300;

    /** Lo que se pide para un protocolo: cuál, y la indicación propia de este tipo si la tiene. */
    public record Requested(UUID idProtocolo, String indicacion) {
    }

    @EqualsAndHashCode.Include
    private final UUID idTipoEquipo;

    private final List<AssignedProtocol> protocolos = new ArrayList<>();

    private ProtocolAssignment(UUID idTipoEquipo, List<AssignedProtocol> protocolos) {
        this.idTipoEquipo = idTipoEquipo;
        this.protocolos.addAll(protocolos);
    }

    /** La asignación de un tipo que todavía no tiene protocolos. */
    public static ProtocolAssignment empty(UUID idTipoEquipo) {
        if (idTipoEquipo == null) {
            throw new IllegalArgumentException("Una asignación de protocolos es de un tipo de equipo");
        }

        return new ProtocolAssignment(idTipoEquipo, List.of());
    }

    /** Leer de la base no es un hecho del dominio: no emite. Se ordena por prioridad. */
    public static ProtocolAssignment rehydrate(UUID idTipoEquipo, List<AssignedProtocol> protocolos) {
        return new ProtocolAssignment(
                idTipoEquipo,
                protocolos.stream()
                        .sorted((a, b) -> Integer.compare(a.prioridad(), b.prioridad()))
                        .toList());
    }

    /** Copia inmutable, en orden de prioridad. */
    public List<AssignedProtocol> getProtocolos() {
        return List.copyOf(protocolos);
    }

    /**
     * Fija los protocolos del tipo, en el orden en que llegan. Una lista vacía los quita todos.
     *
     * <p>Un protocolo no se repite en un tipo, y una indicación en blanco se guarda como ninguna. Si lo
     * pedido es lo que ya había —mismos protocolos, mismo orden, mismas indicaciones— no emite nada.
     */
    public void assign(List<Requested> pedidos) {
        if (pedidos == null) {
            throw new IllegalArgumentException("La lista de protocolos puede estar vacía, pero no faltar");
        }

        Set<UUID> vistos = new HashSet<>();
        List<AssignedProtocol> nuevos = new ArrayList<>();

        for (Requested pedido : pedidos) {
            if (pedido == null || pedido.idProtocolo() == null) {
                throw new IllegalArgumentException("Cada protocolo de la lista necesita su identificador");
            }
            if (!vistos.add(pedido.idProtocolo())) {
                throw new IllegalArgumentException(
                        "El protocolo " + pedido.idProtocolo() + " está dos veces en la lista");
            }

            nuevos.add(new AssignedProtocol(
                    pedido.idProtocolo(), nuevos.size() + 1, validarIndicacion(pedido.indicacion())));
        }

        if (nuevos.equals(protocolos)) {
            return;
        }

        protocolos.clear();
        protocolos.addAll(nuevos);
        registerEvent(new ProtocolsAssignedEvent(
                metadataFor(ProtocolsAssignedEvent.TYPE),
                new ProtocolsAssignedPayload(
                        idTipoEquipo, nuevos.stream().map(AssignedProtocol::idProtocolo).toList())));
    }

    @Override
    protected String aggregateType() {
        return "ProtocolAssignment";
    }

    @Override
    protected String aggregateId() {
        return idTipoEquipo.toString();
    }

    private static String validarIndicacion(String indicacion) {
        if (indicacion == null || indicacion.isBlank()) {
            return null;
        }

        String limpia = indicacion.trim();

        if (limpia.length() > LARGO_INDICACION) {
            throw new IllegalArgumentException(
                    "La indicación de un protocolo tiene como mucho " + LARGO_INDICACION + " caracteres");
        }

        return limpia;
    }
}
