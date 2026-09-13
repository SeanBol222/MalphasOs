package com.malphasos.malphasos.workorder.application.ports.input;

import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AssignWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.CancelWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ScheduleWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.StartWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.List;
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

    List<WorkOrder> findAll();

    WorkOrder findById(UUID id);

    List<WorkOrder> findByClient(UUID idCliente);

    List<WorkOrder> findByHeadquarter(UUID idSede);

    List<WorkOrder> findByEngineer(UUID idIngeniero);

    List<WorkOrder> findByEquipment(UUID idEquipoCliente);

    WorkOrder schedule(ScheduleWorkOrderCommand command);

    WorkOrder assign(AssignWorkOrderCommand command);

    WorkOrder addEquipment(AddEquipmentToWorkOrderCommand command);

    WorkOrder removeEquipment(RemoveEquipmentFromWorkOrderCommand command);

    WorkOrder start(StartWorkOrderCommand command);

    WorkOrder execute(ExecuteWorkOrderCommand command);

    void cancel(CancelWorkOrderCommand command);
}
