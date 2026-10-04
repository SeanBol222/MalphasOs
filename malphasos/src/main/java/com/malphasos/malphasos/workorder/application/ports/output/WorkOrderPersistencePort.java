package com.malphasos.malphasos.workorder.application.ports.output;

import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que este módulo necesita de su almacén.
 *
 * <p>No declara {@code delete} ni {@code update}: retirar una orden es guardarla con el estado en
 * falso, y cambiarla es guardarla otra vez. El agregado que llega ya trae dentro sus equipos, de
 * modo que guardar una orden guarda también su alcance — el adaptador se encarga de conciliar la
 * tabla puente.
 */
public interface WorkOrderPersistencePort {

    List<WorkOrder> findAll();

    Optional<WorkOrder> findById(UUID id);

    /** Las órdenes de un cliente, para la consulta que más se hace. */
    List<WorkOrder> findByClient(UUID idCliente);

    /** Las órdenes de una sede. */
    List<WorkOrder> findByHeadquarter(UUID idSede);

    /** Las órdenes asignadas a un ingeniero: su agenda de trabajo. */
    List<WorkOrder> findByEngineer(UUID idIngeniero);

    /** Las órdenes de estos clientes, para resolver el listado completo acotado. */
    List<WorkOrder> findByClientIn(Collection<UUID> idsClientes);

    /** Las órdenes de un ingeniero que además son de estos clientes. */
    List<WorkOrder> findByEngineerAndClientIn(UUID idIngeniero, Collection<UUID> idsClientes);

    /** El historial de un equipo: en qué órdenes se le ha intervenido. */
    List<WorkOrder> findByEquipment(UUID idEquipoCliente);

    WorkOrder save(WorkOrder orden);
}
