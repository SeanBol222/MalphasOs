package com.malphasos.malphasos.equipment.domain.clientEquipment;

import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentDecommissionedEvent;
import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentPayload;
import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentRegisteredEvent;
import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentRelocatedEvent;
import com.malphasos.malphasos.equipment.domain.clientEquipment.events.ClientEquipmentUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Unidad física que un cliente posee, situada en un área de servicio de una de sus sedes.
 *
 * <p>Es sobre estas unidades sobre las que se hace el mantenimiento preventivo: son el punto donde
 * el catálogo de equipos se encuentra con la organización del cliente.
 *
 * <p>El modelo no cambia —una unidad no se convierte en otra cosa—, pero el área sí: los equipos se
 * mueven entre las áreas de una sede, y ese traslado es un hecho que interesa fuera, porque cambia
 * quién responde por el equipo.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClientEquipment extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private final String serie;

    private final UUID idModelo;

    private UUID idAreaServicio;

    /** Numero de inventario que le da el cliente, opcional. */
    private String numeroInventario;

    private LocalDate fechaCompra;

    private Long valorCompra;

    private boolean estadoActivo;

    private ClientEquipment(
            UUID id,
            String serie,
            UUID idModelo,
            UUID idAreaServicio,
            String numeroInventario,
            LocalDate fechaCompra,
            Long valorCompra,
            boolean estadoActivo) {

        this.id = id;
        this.serie = serie;
        this.idModelo = idModelo;
        this.idAreaServicio = idAreaServicio;
        this.numeroInventario = numeroInventario;
        this.fechaCompra = fechaCompra;
        this.valorCompra = valorCompra;
        this.estadoActivo = estadoActivo;
    }

    public static ClientEquipment register(
            String serie,
            UUID idModelo,
            UUID idAreaServicio,
            String numeroInventario,
            LocalDate fechaCompra,
            Long valorCompra) {

        ClientEquipment unidad = new ClientEquipment(
                UUID.randomUUID(),
                validarSerie(serie),
                exigir(idModelo, "modelo"),
                exigir(idAreaServicio, "area de servicio"),
                normalizar(numeroInventario),
                validarFecha(fechaCompra),
                validarValor(valorCompra),
                true);

        unidad.registerEvent(new ClientEquipmentRegisteredEvent(
                unidad.metadataFor(ClientEquipmentRegisteredEvent.TYPE), unidad.payload()));

        return unidad;
    }

    public static ClientEquipment rehydrate(
            UUID id,
            String serie,
            UUID idModelo,
            UUID idAreaServicio,
            String numeroInventario,
            LocalDate fechaCompra,
            Long valorCompra,
            boolean estadoActivo) {

        return new ClientEquipment(id, serie, idModelo, idAreaServicio, numeroInventario,
                fechaCompra, valorCompra, estadoActivo);
    }

    /** Traslada la unidad a otra área de servicio. */
    public void relocateTo(UUID idAreaServicio) {
        UUID destino = exigir(idAreaServicio, "area de servicio");

        if (destino.equals(this.idAreaServicio)) {
            return;
        }

        this.idAreaServicio = destino;
        registerEvent(new ClientEquipmentRelocatedEvent(
                metadataFor(ClientEquipmentRelocatedEvent.TYPE), payload()));
    }

    /** Corrige los datos de compra y el inventario. Un valor nulo deja el campo como está. */
    public void update(String numeroInventario, LocalDate fechaCompra, Long valorCompra) {
        boolean cambio = false;

        if (numeroInventario != null
                && !Objects.equals(normalizar(numeroInventario), this.numeroInventario)) {
            this.numeroInventario = normalizar(numeroInventario);
            cambio = true;
        }
        if (fechaCompra != null && !fechaCompra.equals(this.fechaCompra)) {
            this.fechaCompra = validarFecha(fechaCompra);
            cambio = true;
        }
        if (valorCompra != null && !valorCompra.equals(this.valorCompra)) {
            this.valorCompra = validarValor(valorCompra);
            cambio = true;
        }

        if (cambio) {
            registerEvent(new ClientEquipmentUpdatedEvent(
                    metadataFor(ClientEquipmentUpdatedEvent.TYPE), payload()));
        }
    }

    /** Da de baja la unidad sin borrarla. Darla de baja dos veces no emite dos eventos. */
    public void decommission() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ClientEquipmentDecommissionedEvent(
                metadataFor(ClientEquipmentDecommissionedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "ClientEquipment";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ClientEquipmentPayload payload() {
        return new ClientEquipmentPayload(serie, idModelo, idAreaServicio);
    }

    private static String validarSerie(String serie) {
        if (serie == null || serie.isBlank()) {
            throw new IllegalArgumentException("Una unidad necesita su numero de serie");
        }

        return serie.trim();
    }

    private static UUID exigir(UUID valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Una unidad necesita su " + campo);
        }

        return valor;
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Un equipo no se compró en el futuro. */
    private static LocalDate validarFecha(LocalDate fechaCompra) {
        if (fechaCompra != null && fechaCompra.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La fecha de compra no puede estar en el futuro: " + fechaCompra);
        }

        return fechaCompra;
    }

    private static Long validarValor(Long valorCompra) {
        if (valorCompra != null && valorCompra < 0) {
            throw new IllegalArgumentException(
                    "El valor de compra no puede ser negativo, y se recibio " + valorCompra);
        }

        return valorCompra;
    }
}
