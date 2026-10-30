package com.malphasos.malphasos.workorder.application.ports.input;

import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AssignWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.CancelWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ScheduleWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.StartWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que se puede hacer con una orden de trabajo.
 *
 * <p>Las operaciones se agrupan en tres familias que el modelo de permisos distingue: consultar,
 * planificar —programar, cambiar el alcance, cancelar— y asignar. Que asignar tenga autoridad
 * propia es la razón de que sea una operación aparte y no un campo más de la orden.
 *
 * <p>No hay operación de cambio general. Una orden no se «edita»: se le añaden o quitan equipos, se
 * le asigna un ingeniero, y avanza de estado. Cada uno de esos es un hecho distinto y por eso tiene
 * su propio método y su propio evento.
 */
public interface WorkOrderServicePort {

    List<WorkOrder> findAll(ReadScope alcance);

    WorkOrder findById(UUID id, ReadScope alcance);

    List<WorkOrder> findByClient(UUID idCliente, ReadScope alcance);

    List<WorkOrder> findByHeadquarter(UUID idSede, ReadScope alcance);

    /**
     * Las órdenes asignadas a un ingeniero, acotadas a los clientes del alcance.
     *
     * <p>Es la única de las cinco que no puede delegar la comprobación: un ingeniero no pertenece a
     * ningún cliente, de modo que no hay nada que preguntar sobre él. Lo que se acota es el
     * resultado.
     */
    List<WorkOrder> findByEngineer(UUID idIngeniero, ReadScope alcance);

    List<WorkOrder> findByEquipment(UUID idEquipoCliente, ReadScope alcance);

    /**
     * Los equipos de cada orden con su reporte activo, por orden (V50: los equipos de una orden son sus
     * reportes). No acota: se pide con órdenes que ya pasaron por una lectura acotada.
     */
    Map<UUID, List<WorkOrderReportsPort.ActiveReport>> equipmentOf(Collection<UUID> idsOrdenes);

    /**
     * Las órdenes cuya fecha de mantenimiento cae entre dos días, ambos incluidos, ordenadas por
     * fecha: la agenda.
     *
     * <p>Es la única consulta que admite un segundo filtro, el ingeniero —«solo mis órdenes»—, porque
     * es la única pantalla que lo necesita. El ingeniero puede ser nulo, y entonces son todas las del
     * periodo. Se acota igual que {@link #findByEngineer}: lo que se recorta es el resultado.
     */
    List<WorkOrder> findInPeriod(LocalDate desde, LocalDate hasta, UUID idIngeniero, ReadScope alcance);

    WorkOrder schedule(ScheduleWorkOrderCommand command);

    WorkOrder assign(AssignWorkOrderCommand command);

    WorkOrder addEquipment(AddEquipmentToWorkOrderCommand command);

    WorkOrder removeEquipment(RemoveEquipmentFromWorkOrderCommand command);

    WorkOrder start(StartWorkOrderCommand command);

    WorkOrder execute(ExecuteWorkOrderCommand command);

    void cancel(CancelWorkOrderCommand command);
}
