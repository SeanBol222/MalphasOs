package com.malphasos.malphasos.workorder.domain.workOrder;

import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderAssignedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderCreatedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderDeactivatedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderEquipmentAddedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderEquipmentPayload;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderEquipmentRemovedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderExecutedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderPayload;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderStartedEvent;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * El mantenimiento que se va a prestar: a qué cliente, en qué sede, sobre qué equipos y con qué
 * periodicidad.
 *
 * <p>Una orden dice <b>qué se va a hacer</b>. Lo que se hizo será el reporte de servicio, que
 * colgará de aquí cuando exista.
 *
 * <p><b>El cliente y la sede se guardan, no se deducen.</b> Podrían obtenerse recorriendo los
 * equipos hasta su área, su sede y su cliente, pero un equipo puede trasladarse después a otra sede
 * del mismo cliente y entonces una orden ya ejecutada cambiaría de sede sola. Lo mismo vale para el
 * área de cada equipo, que se congela al seleccionarlo: ver {@link SelectedEquipment}. La
 * redundancia es deliberada y protege el historial.
 *
 * <p><b>Los equipos son parte de la orden, no un agregado aparte.</b> No tienen sentido fuera de
 * ella y su ciclo de vida es el suyo: se eligen al planificar y dejan de poder cambiarse cuando el
 * trabajo termina. Por eso viven dentro y no se referencian por identificador como el cliente o la
 * sede.
 *
 * <p><b>Lo que este agregado no puede comprobar.</b> Que el cliente y la sede existan y estén
 * activos, que la sede sea de ese cliente, que cada equipo pertenezca a ese cliente y estuviera en
 * el área declarada, y que el ingeniero sea de tipo {@code ENGINEER}: todo eso exige consultar otros
 * módulos y vive en el servicio de aplicación. Aquí solo se defiende lo que se puede decidir con lo
 * que la propia orden tiene delante.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkOrder extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    /** Destinatario del servicio, congelado al crear la orden. */
    private final UUID idCliente;

    /** Sede donde se presta, congelada por la misma razón. */
    private final UUID idSede;

    private LocalDate fechaMantenimiento;

    private Periodicity periodicidad;

    private ServiceType tipoServicio;

    private ExecutionState estadoEjecucion;

    /** Ingeniero a cargo. Nulo mientras la orden está creada pero sin asignar. */
    private UUID idIngeniero;

    private final Set<SelectedEquipment> equipos;

    private boolean estadoActivo;

    private WorkOrder(
            UUID id,
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio,
            ExecutionState estadoEjecucion,
            UUID idIngeniero,
            Collection<SelectedEquipment> equipos,
            boolean estadoActivo) {

        this.id = id;
        this.idCliente = idCliente;
        this.idSede = idSede;
        this.fechaMantenimiento = fechaMantenimiento;
        this.periodicidad = periodicidad;
        this.tipoServicio = tipoServicio;
        this.estadoEjecucion = estadoEjecucion;
        this.idIngeniero = idIngeniero;
        this.equipos = new LinkedHashSet<>(equipos);
        this.estadoActivo = estadoActivo;
    }

    /**
     * Programa un mantenimiento. Nace {@link ExecutionState#CREADA}, sin ingeniero y sin equipos.
     *
     * <p>Se permite crearla vacía porque el formulario de la especificación elige los equipos en un
     * paso posterior al de la sede. Lo que no se permite es empezarla así: ver {@link #start()}.
     */
    public static WorkOrder schedule(
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio) {

        WorkOrder orden = new WorkOrder(
                UUID.randomUUID(),
                exigir(idCliente, "cliente"),
                exigir(idSede, "sede"),
                exigirFecha(fechaMantenimiento),
                exigir(periodicidad, "periodicidad"),
                exigir(tipoServicio, "tipo de servicio"),
                ExecutionState.CREADA,
                null,
                Set.of(),
                true);

        orden.registerEvent(new WorkOrderCreatedEvent(
                orden.metadataFor(WorkOrderCreatedEvent.TYPE), orden.payload()));

        return orden;
    }

    public static WorkOrder rehydrate(
            UUID id,
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio,
            ExecutionState estadoEjecucion,
            UUID idIngeniero,
            Collection<SelectedEquipment> equipos,
            boolean estadoActivo) {

        return new WorkOrder(id, idCliente, idSede, fechaMantenimiento, periodicidad, tipoServicio,
                estadoEjecucion, idIngeniero, equipos, estadoActivo);
    }

    // ---------------------------------------------------------------------------
    // Alcance: qué equipos entran
    // ---------------------------------------------------------------------------

    /**
     * Añade un equipo al alcance, con el área en la que está ahora.
     *
     * <p>Añadir uno que ya está no hace nada, ni siquiera si el área que se pasa es otra: dentro de
     * una orden la identidad de un equipo es el equipo, y el área quedó congelada la primera vez.
     * Para corregirla hay que retirarlo y volver a añadirlo.
     */
    public void addEquipment(UUID idEquipoCliente, UUID idAreaServicio) {
        exigirModificable("anadir equipos");

        SelectedEquipment seleccionado = SelectedEquipment.of(idEquipoCliente, idAreaServicio);

        if (!equipos.add(seleccionado)) {
            return;
        }

        registerEvent(new WorkOrderEquipmentAddedEvent(
                metadataFor(WorkOrderEquipmentAddedEvent.TYPE),
                new WorkOrderEquipmentPayload(idEquipoCliente, idAreaServicio)));
    }

    /** Saca un equipo del alcance. Sacar uno que no está no hace nada. */
    public void removeEquipment(UUID idEquipoCliente) {
        exigirModificable("retirar equipos");

        SelectedEquipment retirado = equipos.stream()
                .filter(equipo -> equipo.getIdEquipoCliente().equals(idEquipoCliente))
                .findFirst()
                .orElse(null);

        if (retirado == null) {
            return;
        }

        equipos.remove(retirado);
        registerEvent(new WorkOrderEquipmentRemovedEvent(
                metadataFor(WorkOrderEquipmentRemovedEvent.TYPE),
                new WorkOrderEquipmentPayload(
                        retirado.getIdEquipoCliente(), retirado.getIdAreaServicio())));
    }

    /**
     * Los equipos del alcance, en el orden en que se añadieron. Copia inmutable: modificarla no
     * cambia la orden.
     *
     * <p>Se devuelve envuelto y no con {@code Set.copyOf}, que no conserva el orden de iteración.
     * Aquí importa: con él, dos lecturas del mismo agregado dan la misma secuencia, y eso hace
     * comparables tanto una prueba como el volcado que persista el adaptador.
     */
    public Set<SelectedEquipment> getEquipos() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(equipos));
    }

    // ---------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------

    /** Pone la orden en manos de un ingeniero. Asignársela a quien ya la tiene no hace nada. */
    public void assignTo(UUID idIngeniero) {
        exigirModificable("asignar la orden");

        UUID ingeniero = exigir(idIngeniero, "ingeniero");

        if (ingeniero.equals(this.idIngeniero)) {
            return;
        }

        this.idIngeniero = ingeniero;
        registerEvent(new WorkOrderAssignedEvent(
                metadataFor(WorkOrderAssignedEvent.TYPE), payload()));
    }

    /**
     * Arranca el trabajo.
     *
     * <p>Exige un ingeniero y al menos un equipo: sin lo primero no hay quien lo haga, y sin lo
     * segundo no hay sobre qué. Son las dos condiciones que convierten una orden planificada en
     * trabajo real, y por eso se comprueban aquí y no al crearla.
     */
    public void start() {
        avanzarA(ExecutionState.EN_EJECUCION, "empezar");

        if (idIngeniero == null) {
            throw new IllegalStateException(
                    "No se puede empezar una orden sin ingeniero asignado");
        }
        if (equipos.isEmpty()) {
            throw new IllegalStateException("No se puede empezar una orden sin equipos");
        }

        this.estadoEjecucion = ExecutionState.EN_EJECUCION;
        registerEvent(new WorkOrderStartedEvent(
                metadataFor(WorkOrderStartedEvent.TYPE), payload()));
    }

    /** Da el trabajo por terminado. Desde aquí la orden ya no cambia. */
    public void execute() {
        avanzarA(ExecutionState.EJECUTADA, "dar por ejecutada");

        this.estadoEjecucion = ExecutionState.EJECUTADA;
        registerEvent(new WorkOrderExecutedEvent(
                metadataFor(WorkOrderExecutedEvent.TYPE), payload()));
    }

    /**
     * Cancela la orden sin borrarla. Cancelarla dos veces no emite dos eventos.
     *
     * <p>No es lo mismo que ejecutarla: una orden ejecutada se hizo, una cancelada no. Por eso se
     * permite cancelar incluso una ya ejecutada — retirar del listado un registro histórico no
     * reescribe lo que ocurrió.
     */
    public void cancel() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new WorkOrderDeactivatedEvent(
                metadataFor(WorkOrderDeactivatedEvent.TYPE), payload()));
    }

    // ---------------------------------------------------------------------------

    @Override
    protected String aggregateType() {
        return "WorkOrder";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private WorkOrderPayload payload() {
        return new WorkOrderPayload(idCliente, idSede, fechaMantenimiento, periodicidad,
                tipoServicio, estadoEjecucion, idIngeniero);
    }

    /**
     * Comprueba que se puede avanzar al estado pedido.
     *
     * <p>Distingue dos negativas que un solo mensaje confundiría: la de una orden que ya terminó y
     * la de un salto de estado. Quien recibe el error necesita saber cuál de las dos es.
     */
    private void avanzarA(ExecutionState destino, String accion) {
        exigirActiva(accion);

        if (estadoEjecucion.esFinal()) {
            throw new IllegalStateException(
                    "Una orden ya ejecutada no admite " + accion + ": el trabajo esta hecho");
        }
        if (!estadoEjecucion.avanzaA(destino)) {
            throw new IllegalStateException("No se puede " + accion + " una orden que esta "
                    + estadoEjecucion + ": el paso siguiente es " + estadoEjecucion.siguiente());
        }
    }

    /**
     * El alcance y la asignación se tocan mientras el trabajo no haya terminado.
     *
     * <p>Se permite en ejecución, no solo al planificar: en campo aparece un equipo que no estaba
     * previsto, o uno de los elegidos resulta inaccesible. Lo que no se admite es cambiar una orden
     * ejecutada, porque entonces su registro dejaría de describir lo que se hizo.
     */
    private void exigirModificable(String accion) {
        exigirActiva(accion);

        if (estadoEjecucion.esFinal()) {
            throw new IllegalStateException(
                    "No se puede " + accion + " en una orden ya ejecutada");
        }
    }

    private void exigirActiva(String accion) {
        if (!estadoActivo) {
            throw new IllegalStateException("No se puede " + accion + " en una orden cancelada");
        }
    }

    private static <T> T exigir(T valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Una orden de trabajo necesita su " + campo);
        }

        return valor;
    }

    /**
     * La fecha es obligatoria pero no se acota.
     *
     * <p>A diferencia de la fecha de compra de un equipo, que no puede estar en el futuro, la de un
     * mantenimiento suele estarlo —se programa— y a veces está en el pasado, cuando la orden se
     * registra después de haber ido. Ninguno de los dos extremos es un error.
     */
    private static LocalDate exigirFecha(LocalDate fechaMantenimiento) {
        return exigir(fechaMantenimiento, "fecha de mantenimiento");
    }
}
