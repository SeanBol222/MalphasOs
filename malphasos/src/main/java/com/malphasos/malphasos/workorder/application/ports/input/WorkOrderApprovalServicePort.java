package com.malphasos.malphasos.workorder.application.ports.input;

import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveFromAccountCommand;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveOnSiteCommand;
import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La aprobación de una orden por el supervisor de su cliente (V54, 2026-11-01).
 *
 * <p>Las dos formas de aprobar son dos operaciones porque se distinguen en lo que importa: en sitio el
 * supervisor dibuja su firma en la sesión de otro —y no se puede estampar la guardada, porque el ingeniero
 * firmaría por él—; desde su cuenta, es él quien llama y se usa la firma que guardó.
 */
public interface WorkOrderApprovalServicePort {

    /** La aprobación vigente de la orden, si la tiene. La orden tiene que estar al alcance de quien pregunta. */
    Optional<WorkOrderApproval> findActive(UUID idOrden, ReadScope alcance);

    /** Aprueba en sitio, con la firma dibujada en la sesión de quien la registra. */
    WorkOrderApproval approveOnSite(ApproveOnSiteCommand command, ReadScope alcance);

    /** Aprueba el propio supervisor desde su cuenta, con la firma que guardó. */
    WorkOrderApproval approveFromAccount(ApproveFromAccountCommand command, ReadScope alcance);

    /** Las órdenes ejecutadas, activas y sin aprobar al alcance de quien pregunta: lo que falta para facturar. */
    List<WorkOrder> pending(ReadScope alcance);

    /**
     * Retira la aprobación vigente de la orden, si la hay. La llama el reporte al sustituir uno de una orden
     * aprobada: el supervisor aprobó un documento que ya no es el mismo.
     */
    void withdraw(UUID idOrden);
}
