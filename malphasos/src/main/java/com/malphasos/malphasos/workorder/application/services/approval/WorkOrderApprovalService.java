package com.malphasos.malphasos.workorder.application.services.approval;

import com.malphasos.malphasos.client.application.ports.input.ClientOwnershipPort;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderApprovalServicePort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderApprovalPersistencePort;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveFromAccountCommand;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveOnSiteCommand;
import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import com.malphasos.malphasos.workorder.domain.exception.NoSavedSignatureException;
import com.malphasos.malphasos.workorder.domain.exception.NotSupervisorOfClientException;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las reglas de la aprobación, que piden mirar fuera de ella (V54, 2026-11-01):
 *
 * <ol>
 *   <li>Se aprueba una orden <b>ejecutada</b> y activa: se aprueba trabajo terminado. Ejecutar no exige
 *       aprobar, para que una visita no se quede esperando a que el supervisor revise.
 *   <li>Quien aprueba es un <b>supervisor activo del cliente de la orden</b>, en ese momento.
 *   <li>Una sola vigente por orden: aprobar dos veces es un conflicto, no una sustitución.
 *   <li>En sitio, la firma se <b>dibuja</b>; desde la cuenta se usa la guardada, y quien aprueba es quien
 *       llama.
 *   <li>Sustituir un reporte retira la aprobación, por {@link #withdraw}.
 * </ol>
 *
 * <p>Solo aprobar, sin rechazar: decidido por el usuario para esta primera versión. Rechazar exigiría
 * reabrir o sustituir informes desde la cuenta del cliente.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderApprovalService implements WorkOrderApprovalServicePort {

    private final WorkOrderApprovalPersistencePort approvalPersistencePort;
    private final WorkOrderServicePort workOrderServicePort;
    private final ClientOwnershipPort clientOwnershipPort;
    private final PersonCommunicationPort personCommunicationPort;
    private final PersonSignatureServicePort personSignatureServicePort;
    private final DocumentImageStorePort documentImageStorePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkOrderApproval> findActive(UUID idOrden, ReadScope alcance) {
        workOrderServicePort.findById(idOrden, alcance);

        return approvalPersistencePort.findActive(idOrden);
    }

    @Override
    @Transactional
    public WorkOrderApproval approveOnSite(ApproveOnSiteCommand command, ReadScope alcance) {
        DocumentImage firma = DocumentImage.firma(command.firma());

        return approve(command.idOrden(), command.idSupervisor(), operadorDe(command.idOperador()), firma, alcance);
    }

    @Override
    @Transactional
    public WorkOrderApproval approveFromAccount(ApproveFromAccountCommand command, ReadScope alcance) {
        PersonSignature guardada = personSignatureServicePort.find(command.idSupervisor())
                .orElseThrow(() -> new NoSavedSignatureException(command.idSupervisor()));

        return approve(command.idOrden(), command.idSupervisor(), command.idSupervisor(),
                DocumentImage.firma(guardada.imagen()), alcance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> pending(ReadScope alcance) {
        List<WorkOrder> ejecutadas = workOrderServicePort.findAll(alcance).stream()
                .filter(WorkOrder::isEstadoActivo)
                .filter(orden -> orden.getEstadoEjecucion() == ExecutionState.EJECUTADA)
                .toList();
        Set<UUID> aprobadas = approvalPersistencePort.findApprovedAmong(
                ejecutadas.stream().map(WorkOrder::getId).toList());

        return ejecutadas.stream().filter(orden -> !aprobadas.contains(orden.getId())).toList();
    }

    @Override
    @Transactional
    public void withdraw(UUID idOrden) {
        approvalPersistencePort.findActive(idOrden).ifPresent(aprobacion -> {
            aprobacion.withdraw();
            persistAndPublish(aprobacion);
        });
    }

    private WorkOrderApproval approve(
            UUID idOrden, UUID idSupervisor, UUID idOperador, DocumentImage firma, ReadScope alcance) {

        WorkOrder orden = workOrderServicePort.findById(idOrden, alcance);
        if (!orden.isEstadoActivo() || orden.getEstadoEjecucion() != ExecutionState.EJECUTADA) {
            throw new IllegalStateException(
                    "La orden " + idOrden + " no está ejecutada: se aprueba el trabajo terminado");
        }
        requireSupervisorOf(idSupervisor, orden);
        if (approvalPersistencePort.findActive(idOrden).isPresent()) {
            throw new IllegalStateException("La orden " + idOrden + " ya está aprobada");
        }

        documentImageStorePort.store(firma);

        return persistAndPublish(WorkOrderApproval.approve(
                idOrden, idSupervisor, idOperador, firma.huella(), LocalDateTime.now()));
    }

    /**
     * Un supervisor activo del cliente de la orden. Se mira el tipo además del nombramiento porque una
     * persona nombrada a la que después se le cambió el tipo deja de supervisar, aunque su fila siga.
     */
    private void requireSupervisorOf(UUID idSupervisor, WorkOrder orden) {
        PersonCommunicationResponse persona = personCommunicationPort.findById(idSupervisor);
        boolean supervisa = persona.estadoActivo()
                && persona.tipoPersona() == PersonType.SUPERVISOR
                && clientOwnershipPort.clientsSupervisedBy(idSupervisor).contains(orden.getIdCliente());

        if (!supervisa) {
            throw new NotSupervisorOfClientException(idSupervisor, orden.getId());
        }
    }

    /**
     * Quién tiene la sesión, si tiene ficha. El SuperUsuario no la tiene —se crea a mano en Keycloak— y su
     * aprobación en sitio queda con el operador vacío, que la foránea a persona exige.
     */
    private UUID operadorDe(UUID sub) {
        // Preguntando y no capturando: la excepción de «no existe» dejaría la transacción para deshacer.
        return personCommunicationPort.exists(sub) ? sub : null;
    }

    /** Los eventos salen después de guardar, y son los del agregado recibido. */
    private WorkOrderApproval persistAndPublish(WorkOrderApproval aprobacion) {
        WorkOrderApproval guardada = approvalPersistencePort.save(aprobacion);
        eventDispatcherPort.dispatchAll(aprobacion.pullEvents());

        return guardada;
    }
}
