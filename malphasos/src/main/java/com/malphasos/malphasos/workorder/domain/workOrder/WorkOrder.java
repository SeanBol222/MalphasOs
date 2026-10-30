package com.malphasos.malphasos.workorder.domain.workOrder;

import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderWithoutEngineerException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderWithoutEquipmentException;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderAssignedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderCreatedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderDeactivatedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderExecutedEvent;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderPayload;
import com.malphasos.malphasos.workorder.domain.workOrder.events.WorkOrderStartedEvent;
import java.time.LocalDate;
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
 * <p><b>La sede se guarda, no se deduce.</b> Podría obtenerse recorriendo los equipos hasta su área y
 * su sede, pero una orden se presta en un sitio aunque todavía no tenga equipos. El cliente es el de su
 * sede desde {@code V47}.
 *
 * <p><b>Los equipos no viven aquí desde {@code V50}.</b> Un equipo está en una orden si y solo si tiene
 * en ella un reporte activo: añadirlo es abrir su borrador y sacarlo, retirarlo. Lo hace el servicio por
 * {@code WorkOrderReportsPort}, y este agregado solo dice si su alcance todavía se puede tocar —{@link
 * #requireScopeChangeable(String)}— y si con tantos equipos se puede empezar —{@link #start(int)}—.
 *
 * <p><b>Lo que este agregado no puede comprobar.</b> Que el cliente y la sede existan y estén
 * activos, que la sede sea de ese cliente, que cada equipo pertenezca a ese cliente y esté en esa
 * sede, y que el ingeniero sea de tipo {@code ENGINEER}: todo eso exige consultar otros
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

    /**
     * Un correctivo en garantía (2026-10-16): una marca del correctivo, no un cuarto tipo de servicio.
     * Se decide al programar y no cambia, como el tipo.
     */
    private final boolean garantia;

    private ExecutionState estadoEjecucion;

    /** Ingeniero a cargo. Nulo mientras la orden está creada pero sin asignar. */
    private UUID idIngeniero;

    private boolean estadoActivo;

    /** Lo que la orden era al ejecutarse (V49). Nulo mientras no se ha ejecutado. */
    private WorkOrderDocument documento;

    /** El documento se compuso después de ejecutarla: las órdenes ejecutadas antes de V49. */
    private final boolean documentoTardio;

    private WorkOrder(
            UUID id,
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio,
            boolean garantia,
            ExecutionState estadoEjecucion,
            UUID idIngeniero,
            boolean estadoActivo,
            WorkOrderDocument documento,
            boolean documentoTardio) {

        this.id = id;
        this.idCliente = idCliente;
        this.idSede = idSede;
        this.fechaMantenimiento = fechaMantenimiento;
        this.periodicidad = periodicidad;
        this.tipoServicio = tipoServicio;
        this.garantia = garantia;
        this.estadoEjecucion = estadoEjecucion;
        this.idIngeniero = idIngeniero;
        this.estadoActivo = estadoActivo;
        this.documento = documento;
        this.documentoTardio = documentoTardio;
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

        return schedule(idCliente, idSede, fechaMantenimiento, periodicidad, tipoServicio, false);
    }

    /** Lo mismo, diciendo si es un correctivo en garantía. Solo un correctivo puede estarlo. */
    public static WorkOrder schedule(
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio,
            boolean garantia) {

        exigir(tipoServicio, "tipo de servicio");
        if (garantia && tipoServicio != ServiceType.CORRECTIVO) {
            throw new IllegalArgumentException("Solo un correctivo puede estar en garantia, y esta orden es "
                    + tipoServicio);
        }

        WorkOrder orden = new WorkOrder(
                UUID.randomUUID(),
                exigir(idCliente, "cliente"),
                exigir(idSede, "sede"),
                exigirFecha(fechaMantenimiento),
                exigir(periodicidad, "periodicidad"),
                tipoServicio,
                garantia,
                ExecutionState.CREADA,
                null,
                true,
                null,
                false);

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
            boolean estadoActivo) {

        return rehydrate(id, idCliente, idSede, fechaMantenimiento, periodicidad, tipoServicio, false,
                estadoEjecucion, idIngeniero, estadoActivo);
    }

    public static WorkOrder rehydrate(
            UUID id,
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio,
            boolean garantia,
            ExecutionState estadoEjecucion,
            UUID idIngeniero,
            boolean estadoActivo) {

        return rehydrate(id, idCliente, idSede, fechaMantenimiento, periodicidad, tipoServicio, garantia,
                estadoEjecucion, idIngeniero, estadoActivo, null, false);
    }

    /** Con su documento, si ya se ejecutó (V49). */
    public static WorkOrder rehydrate(
            UUID id,
            UUID idCliente,
            UUID idSede,
            LocalDate fechaMantenimiento,
            Periodicity periodicidad,
            ServiceType tipoServicio,
            boolean garantia,
            ExecutionState estadoEjecucion,
            UUID idIngeniero,
            boolean estadoActivo,
            WorkOrderDocument documento,
            boolean documentoTardio) {

        return new WorkOrder(id, idCliente, idSede, fechaMantenimiento, periodicidad, tipoServicio,
                garantia, estadoEjecucion, idIngeniero, estadoActivo, documento, documentoTardio);
    }

    public static final int LARGO_NUMERO = 6;

    /**
     * El número con que se cita la orden: los seis primeros caracteres de su identificador, en
     * mayúsculas, como el del reporte (2026-10-16). Único: lo garantiza el índice de V34, y quien la
     * programa elige otro identificador si el prefijo está tomado.
     */
    public String numero() {
        return id.toString().substring(0, LARGO_NUMERO).toUpperCase(java.util.Locale.ROOT);
    }

    // ---------------------------------------------------------------------------
    // Alcance: qué equipos entran
    // ---------------------------------------------------------------------------

    /**
     * Que todavía se puedan añadir o sacar equipos, o asignar la orden: mientras el trabajo no haya
     * terminado.
     *
     * <p>Se permite en ejecución, no solo al planificar: en campo aparece un equipo que no estaba
     * previsto, o uno de los elegidos resulta inaccesible. Lo que no se admite es cambiar una orden
     * ejecutada, porque entonces su registro dejaría de describir lo que se hizo.
     *
     * <p>Público desde {@code V50}: los equipos ya no viven en el agregado, y quien los añade o los saca
     * —el servicio, abriendo y retirando reportes— le pregunta antes a la orden si se puede.
     */
    public void requireScopeChangeable(String accion) {
        exigirActiva(accion);

        if (estadoEjecucion.esFinal()) {
            throw new IllegalStateException(
                    "No se puede " + accion + " en una orden ya ejecutada");
        }
    }

    // ---------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------

    /** Pone la orden en manos de un ingeniero. Asignársela a quien ya la tiene no hace nada. */
    public void assignTo(UUID idIngeniero) {
        requireScopeChangeable("asignar la orden");

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
     *
     * @param equipos cuántos equipos tiene la orden, que desde {@code V50} los cuenta el servicio: son
     *     los que tienen en ella un reporte activo
     */
    public void start(int equipos) {
        avanzarA(ExecutionState.EN_EJECUCION, "empezar");

        if (idIngeniero == null) {
            throw new WorkOrderWithoutEngineerException();
        }
        if (equipos <= 0) {
            throw new WorkOrderWithoutEquipmentException();
        }

        this.estadoEjecucion = ExecutionState.EN_EJECUCION;
        registerEvent(new WorkOrderStartedEvent(
                metadataFor(WorkOrderStartedEvent.TYPE), payload()));
    }

    /**
     * Da el trabajo por terminado, congelando lo que la orden es ahora. Desde aquí la orden ya no cambia,
     * y se lee de su documento (V49).
     *
     * <p>El estado se comprueba antes que el documento: a una orden que no puede ejecutarse se le dice
     * por qué no, no que le falta algo que tampoco serviría.
     */
    public void execute(WorkOrderDocument documento) {
        avanzarA(ExecutionState.EJECUTADA, "dar por ejecutada");

        if (documento == null) {
            throw new IllegalArgumentException("Una orden se ejecuta congelando su documento");
        }

        this.documento = documento;
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
                tipoServicio, garantia, estadoEjecucion, idIngeniero);
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
