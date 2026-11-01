package com.malphasos.malphasos.workorder.application.services.workOrder;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AssignWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.CancelWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ScheduleWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.StartWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderHasOpenReportsException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta las órdenes de trabajo.
 *
 * <p>Aquí viven las <b>reglas</b> que ni el esquema ni el agregado pueden defender, porque
 * todas exigen preguntar a otro módulo. El esquema sostenía una de las cruzadas —que la sede fuera
 * del cliente, con una clave foránea compuesta— hasta {@code V47}, que dejó de guardar el cliente en la
 * orden: desde entonces es el de su sede y no se puede contradecir. El agregado solo decide con lo que
 * la propia orden tiene delante.
 *
 * <p>Este es el módulo con más vecinos del proyecto: consulta a {@code client} por el cliente, la
 * sede y el área, a {@code equipment} por cada unidad, y a {@code person} por el ingeniero. Y desde
 * {@code V50} sus equipos son los reportes de {@code report}, que depende de este: los abre, los retira
 * y los cuenta por {@link WorkOrderReportsPort}, que declara este módulo y contesta aquel.
 *
 * <p><b>Las referencias se comprueban activas, no solo existentes.</b> Una clave foránea confirma
 * que la fila está; con borrado lógico eso deja de significar que siga en uso. Es la misma
 * distinción que ya obligó a subir reglas al servicio en {@code client} y en {@code equipment}.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderService implements WorkOrderServicePort {

    private final WorkOrderPersistencePort workOrderPersistencePort;
    private final ClientServicePort clientServicePort;
    private final HeadquarterServicePort headquarterServicePort;
    private final ServiceAreaServicePort serviceAreaServicePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final PersonCommunicationPort personCommunicationPort;
    private final EventDispatcherPort eventDispatcherPort;
    private final WorkOrderReportsPort workOrderReportsPort;
    private final WorkOrderDocumentComposer workOrderDocumentComposer;

    // ---------------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> findAll(ReadScope alcance) {
        if (!alcance.coversEverything()) {
            return workOrderPersistencePort.findByClientIn(alcance.visibleClients());
        }

        return workOrderPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrder findById(UUID id, ReadScope alcance) {
        WorkOrder orden = workOrderPersistencePort.findById(id)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        // Una orden guarda su cliente, de modo que aquí el dueño no cuesta ninguna consulta extra.
        // Es el único de los cuatro módulos acotados donde sale gratis.
        if (!alcance.covers(orden.getIdCliente())) {
            throw new WorkOrderNotFoundException(id);
        }

        return orden;
    }

    /**
     * Una orden sin ejecutar se compone con lo de hoy, como la ficha la enseña, y sin fecha de ejecución:
     * el compositor pone la de hoy porque es lo que guarda al ejecutar, y aquí todavía no se ha ejecutado.
     */
    @Override
    @Transactional(readOnly = true)
    public WorkOrderDocument documentOf(WorkOrder orden) {
        if (orden.getDocumento() != null) {
            return orden.getDocumento();
        }
        WorkOrderDocument hoy = workOrderDocumentComposer.compose(orden);

        return new WorkOrderDocument(hoy.version(), hoy.numero(), hoy.tipoServicio(), hoy.garantia(),
                hoy.periodicidad(), hoy.fechaMantenimiento(), null, hoy.cliente(), hoy.sede(), hoy.ingeniero(),
                hoy.equipos());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> findByClient(UUID idCliente, ReadScope alcance) {
        // Pasarle el alcance es toda la comprobación: si el cliente es de otro, client responde
        // «no existe» y este módulo no tiene que saber nada más.
        clientServicePort.findById(idCliente, alcance);

        return workOrderPersistencePort.findByClient(idCliente);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> findByHeadquarter(UUID idSede, ReadScope alcance) {
        headquarterServicePort.findById(idSede, alcance);

        return workOrderPersistencePort.findByHeadquarter(idSede);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> findInPeriod(LocalDate desde, LocalDate hasta, UUID idIngeniero, ReadScope alcance) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Un periodo necesita su primer y su último día");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException(
                    "El periodo termina el " + hasta + ", antes de empezar el " + desde);
        }
        if (idIngeniero != null) {
            personCommunicationPort.findById(idIngeniero);
        }

        // Igual que findByEngineer: el periodo no es de ningún cliente, así que se acota el resultado.
        return workOrderPersistencePort.findInPeriod(
                desde, hasta, idIngeniero, alcance.coversEverything() ? null : alcance.visibleClients());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> findByEngineer(UUID idIngeniero, ReadScope alcance) {
        personCommunicationPort.findById(idIngeniero);

        // La única que no puede delegar: un ingeniero no es de ningún cliente, así que no hay a
        // quién preguntar por él y lo que se acota es el resultado. Un supervisor que pregunte
        // por un ingeniero de la casa ve las órdenes de ese ingeniero en sus propios clientes, que
        // es exactamente lo que le toca ver, y no la agenda completa de nadie.
        if (!alcance.coversEverything()) {
            return workOrderPersistencePort.findByEngineerAndClientIn(idIngeniero, alcance.visibleClients());
        }

        return workOrderPersistencePort.findByEngineer(idIngeniero);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> findByEquipment(UUID idEquipoCliente, ReadScope alcance) {
        clientEquipmentServicePort.findById(idEquipoCliente, alcance);

        return workOrderPersistencePort.findAllById(workOrderReportsPort.workOrdersOf(idEquipoCliente));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<WorkOrderReportsPort.ActiveReport>> equipmentOf(Collection<UUID> idsOrdenes) {
        return workOrderReportsPort.activeReports(idsOrdenes);
    }

    // ---------------------------------------------------------------------------
    // Planificación
    // ---------------------------------------------------------------------------

    /**
     * Programa un mantenimiento tras comprobar que el destinatario existe y opera.
     *
     * <p>Que la sede sea de ese cliente se comprueba aquí y solo aquí desde {@code V47}: la orden ya
     * no guarda el cliente, que es el de su sede, así que una sede de otro cliente no daría un
     * conflicto sino una orden de otro cliente distinto del pedido.
     */
    @Override
    @Transactional
    public WorkOrder schedule(ScheduleWorkOrderCommand command) {
        requireActiveClient(command.idCliente());
        requireActiveHeadquarterOf(command.idSede(), command.idCliente());

        return persistAndPublish(conNumeroLibre(() -> WorkOrder.schedule(
                command.idCliente(),
                command.idSede(),
                command.fechaMantenimiento(),
                command.periodicidad(),
                command.tipoServicio(),
                command.garantia())));
    }

    static final int INTENTOS_DE_NUMERO = 10;

    /**
     * Programa la orden con un identificador cuyo número —su prefijo— no tenga ya otra (V34).
     *
     * <p>Como el reporte: un número impreso en un informe entregado no puede alargarse después, así que
     * se elige otro identificador. Las órdenes descartadas no emiten nada, porque no se guardan.
     */
    private WorkOrder conNumeroLibre(java.util.function.Supplier<WorkOrder> nueva) {
        for (int intento = 0; intento < INTENTOS_DE_NUMERO; intento++) {
            WorkOrder orden = nueva.get();

            if (!workOrderPersistencePort.numberTaken(orden.numero())) {
                return orden;
            }
        }

        throw new IllegalStateException("No se encontro un numero de orden libre en "
                + INTENTOS_DE_NUMERO + " intentos");
    }

    /**
     * Añade un equipo abriendo su reporte en borrador (V50): un equipo está en una orden si y solo si
     * tiene en ella un reporte activo. Añadir uno que ya está no hace nada.
     *
     * <p>El borrador nace aunque la orden no haya empezado, y no se rellena hasta que empiece: lo
     * comprueba {@code report} en cada escritura. La orden no cambia, así que no se guarda ni emite nada:
     * el hecho es el reporte abierto, y lo publica su módulo.
     */
    @Override
    @Transactional
    public WorkOrder addEquipment(AddEquipmentToWorkOrderCommand command) {
        // Las seis escrituras de este servicio recuperan la orden sin restriccion: el alcance acota
        // lecturas, y avanzar o alterar una orden exige work-order.write o work-order.assign.
        WorkOrder orden = findById(command.id(), ReadScope.unrestricted());
        ClientEquipment unidad =
                clientEquipmentServicePort.findById(command.idEquipoCliente(), ReadScope.unrestricted());

        if (!unidad.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se puede incluir en una orden una unidad dada de baja: " + unidad.getId());
        }

        requireEquipmentInScopeOf(unidad, orden);
        orden.requireScopeChangeable("anadir equipos");

        workOrderReportsPort.openDraft(orden.getId(), orden.getTipoServicio(), unidad.getId());

        return orden;
    }

    /**
     * Saca un equipo retirando su borrador (V50). Uno con el reporte cerrado no sale: un reporte cerrado
     * cuenta un servicio prestado, y se sustituye. Sacar uno que no está no hace nada.
     */
    @Override
    @Transactional
    public WorkOrder removeEquipment(RemoveEquipmentFromWorkOrderCommand command) {
        WorkOrder orden = findById(command.id(), ReadScope.unrestricted());
        orden.requireScopeChangeable("retirar equipos");

        workOrderReportsPort.retireDraft(orden.getId(), command.idEquipoCliente());

        return orden;
    }

    @Override
    @Transactional
    public void cancel(CancelWorkOrderCommand command) {
        WorkOrder orden = findById(command.id(), ReadScope.unrestricted());
        orden.cancel();

        persistAndPublish(orden);
    }

    // ---------------------------------------------------------------------------
    // Asignación y ejecución
    // ---------------------------------------------------------------------------

    /**
     * Pone la orden en manos de un ingeniero.
     *
     * <p>Comprueba que la persona sea de tipo {@code ENGINEER}.
     */
    @Override
    @Transactional
    public WorkOrder assign(AssignWorkOrderCommand command) {
        WorkOrder orden = findById(command.id(), ReadScope.unrestricted());
        requireActiveEngineer(command.idIngeniero());

        orden.assignTo(command.idIngeniero());

        return persistAndPublish(orden);
    }

    @Override
    @Transactional
    public WorkOrder start(StartWorkOrderCommand command) {
        WorkOrder orden = findById(command.id(), ReadScope.unrestricted());
        orden.start(workOrderReportsPort.activeReports(orden.getId()).size());

        return persistAndPublish(orden);
    }

    @Override
    @Transactional
    public WorkOrder execute(ExecuteWorkOrderCommand command) {
        WorkOrder orden = findById(command.id(), ReadScope.unrestricted());
        exigirReportesCerrados(orden);
        // Se compone solo si ejecutar es posible: si no, el agregado dice por que, sin leer nada de fuera.
        orden.execute(ejecutable(orden) ? workOrderDocumentComposer.compose(orden) : null);

        return persistAndPublish(orden);
    }

    // ---------------------------------------------------------------------------

    /**
     * Una orden no se da por ejecutada con equipos sin reporte cerrado (decidido por el usuario el
     * 2026-10-14): ejecutada quiere decir que cada equipo tiene el suyo. Desde {@code V50} los equipos de
     * la orden son sus reportes activos, así que basta con que ninguno siga en borrador.
     *
     * <p>Solo se pregunta cuando ejecutar sería posible. Si la orden aún no empezó, o ya se ejecutó, el
     * agregado responde con su propio conflicto de estado, que es el motivo de verdad; contar reportes
     * de una orden sin empezar daría un mensaje que manda a hacer algo que tampoco se puede.
     */
    private void exigirReportesCerrados(WorkOrder orden) {
        if (!ejecutable(orden)) {
            return;
        }

        long pendientes = workOrderReportsPort.activeReports(orden.getId()).stream()
                .filter(reporte -> !reporte.cerrado())
                .count();

        if (pendientes > 0) {
            throw new WorkOrderHasOpenReportsException(orden.getId(), (int) pendientes);
        }
    }

    /** Si el agregado dejaría ejecutarla: activa y en ejecución. */
    private static boolean ejecutable(WorkOrder orden) {
        return orden.getEstadoEjecucion() == ExecutionState.EN_EJECUCION && orden.isEstadoActivo();
    }

    /**
     * Persiste y publica lo que el agregado decidió que ocurrió.
     *
     * <p>Se publican los eventos del agregado recibido y no los del devuelto por el almacén: el
     * segundo se rehidrata y por eso viene sin ninguno.
     */
    private WorkOrder persistAndPublish(WorkOrder orden) {
        WorkOrder guardada = workOrderPersistencePort.save(orden);
        orden.pullEvents().forEach(eventDispatcherPort::dispatch);

        return guardada;
    }

    private void requireActiveClient(UUID idCliente) {
        // Sin restriccion: es una validacion de escritura, y escribir una orden no lo hace nadie
        // del grupo clients.
        Client cliente = clientServicePort.findById(idCliente, ReadScope.unrestricted());

        if (!cliente.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se programa un mantenimiento a un cliente retirado: " + idCliente);
        }
    }

    /** La sede existe, opera, y es de ese cliente. */
    private void requireActiveHeadquarterOf(UUID idSede, UUID idCliente) {
        // Validacion de escritura: programar una orden exige work-order.write.
        Headquarter sede = headquarterServicePort.findById(idSede, ReadScope.unrestricted());

        if (!sede.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se programa un mantenimiento en una sede cerrada: " + idSede);
        }
        if (!sede.getIdCliente().equals(idCliente)) {
            throw new IllegalArgumentException(
                    "La sede " + idSede + " no es del cliente " + idCliente);
        }
    }

    /**
     * El equipo está donde el mantenimiento se va a prestar: mismo cliente y misma sede.
     *
     * <p><b>Son tres comprobaciones y ninguna sobra.</b> Que el área esté abierta, porque incluir un
     * equipo de un área cerrada sería programar trabajo donde ya no se opera. Que el equipo sea del
     * cliente de la orden. Y que su área sea <b>de la sede de la orden</b>: una orden se presta en
     * un sitio, y un equipo de otra sede no se va a intervenir ese día por mucho que el cliente
     * coincida.
     *
     * <p>El orden importa y no es casual. El dueño se comprueba antes que la sede porque un equipo
     * de otro cliente está necesariamente en otra sede, y las dos negativas serían ciertas: quien
     * recibe el error necesita la más informativa de las dos. Con este orden <b>cada comprobación
     * sigue siendo alcanzable</b> y cada una da el mensaje que corresponde a su caso.
     *
     * <p>La pertenencia al cliente la responde {@code client} de una vez con {@code
     * findOwningClient}, para no obligar a este módulo a caminar la jerarquía área → sede → cliente.
     * La sede, en cambio, la trae ya el propio área, así que no cuesta ninguna consulta más.
     */
    private void requireEquipmentInScopeOf(ClientEquipment unidad, WorkOrder orden) {
        ServiceArea area = serviceAreaServicePort.findById(unidad.getIdAreaServicio(), ReadScope.unrestricted());

        if (!area.isEstadoActivo()) {
            throw new IllegalArgumentException("La unidad " + unidad.getId()
                    + " esta en un area de servicio cerrada");
        }

        UUID duenoDelEquipo = serviceAreaServicePort.findOwningClient(unidad.getIdAreaServicio());

        if (!duenoDelEquipo.equals(orden.getIdCliente())) {
            throw new IllegalArgumentException("La unidad " + unidad.getId() + " es del cliente "
                    + duenoDelEquipo + " y la orden es del cliente " + orden.getIdCliente());
        }

        if (!area.getIdSede().equals(orden.getIdSede())) {
            throw new IllegalArgumentException("La unidad " + unidad.getId() + " esta en la sede "
                    + area.getIdSede() + " y el mantenimiento se presta en la sede "
                    + orden.getIdSede());
        }
    }

    private void requireActiveEngineer(UUID idIngeniero) {
        PersonCommunicationResponse persona = personCommunicationPort.findById(idIngeniero);

        if (!persona.estadoActivo()) {
            throw new IllegalArgumentException(
                    "No se asigna una orden a una persona retirada: " + idIngeniero);
        }
        if (persona.tipoPersona() != PersonType.ENGINEER) {
            throw new IllegalArgumentException(
                    "Solo un ingeniero ejecuta un mantenimiento, y " + idIngeniero + " no lo es");
        }
    }
}
