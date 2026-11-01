package com.malphasos.malphasos.workorder.domain.approval;

import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import com.malphasos.malphasos.workorder.domain.approval.events.WorkOrderApprovalPayload;
import com.malphasos.malphasos.workorder.domain.approval.events.WorkOrderApprovalWithdrawnEvent;
import com.malphasos.malphasos.workorder.domain.approval.events.WorkOrderApprovedEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * La aprobación de una orden ejecutada por un supervisor de su cliente (V54, 2026-11-01): lo que permite
 * facturarla.
 *
 * <p>Es un agregado propio y no un estado de la orden porque tiene vida propia: se retira —sustituir un
 * reporte de una orden aprobada la retira— y la retirada se queda como historial, mientras la orden sigue
 * ejecutada. Las reglas que piden mirar fuera —que la orden esté ejecutada, que quien aprueba supervise a
 * su cliente— son del servicio.
 *
 * <p><b>No guarda la forma</b>: aprobó desde su cuenta si quien tenía la sesión es el propio supervisor, y
 * en sitio si no. Una columna aparte podría contradecirlo.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkOrderApproval extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private final UUID idOrdenTrabajo;

    private final UUID idSupervisor;

    /** Quién tenía la sesión. Nulo si era el SuperUsuario, que no tiene ficha. */
    private final UUID idOperador;

    /** La firma, por su huella en el almacén de imágenes: esa versión, aunque después cambie. */
    private final String huellaFirma;

    private final LocalDateTime aprobada;

    private boolean estadoActivo;

    private WorkOrderApproval(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idSupervisor,
            UUID idOperador,
            String huellaFirma,
            LocalDateTime aprobada,
            boolean estadoActivo) {

        this.id = Objects.requireNonNull(id);
        this.idOrdenTrabajo = Objects.requireNonNull(idOrdenTrabajo, "Una aprobación es de una orden");
        this.idSupervisor = Objects.requireNonNull(idSupervisor, "Una aprobación la da un supervisor");
        this.idOperador = idOperador;
        this.huellaFirma = Objects.requireNonNull(huellaFirma, "Una aprobación lleva la firma de quien aprueba");
        this.aprobada = Objects.requireNonNull(aprobada, "Una aprobación lleva cuándo se dio");
        this.estadoActivo = estadoActivo;
    }

    /** Aprueba la orden. */
    public static WorkOrderApproval approve(
            UUID idOrdenTrabajo, UUID idSupervisor, UUID idOperador, String huellaFirma, LocalDateTime cuando) {

        WorkOrderApproval aprobacion = new WorkOrderApproval(
                UUID.randomUUID(), idOrdenTrabajo, idSupervisor, idOperador, huellaFirma, cuando, true);
        aprobacion.registerEvent(new WorkOrderApprovedEvent(
                aprobacion.metadataFor(WorkOrderApprovedEvent.TYPE), aprobacion.payload()));

        return aprobacion;
    }

    /** Reconstruye una aprobación ya existente, sin registrar eventos. */
    public static WorkOrderApproval rehydrate(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idSupervisor,
            UUID idOperador,
            String huellaFirma,
            LocalDateTime aprobada,
            boolean estadoActivo) {

        return new WorkOrderApproval(id, idOrdenTrabajo, idSupervisor, idOperador, huellaFirma, aprobada, estadoActivo);
    }

    /** Si la aprobó el supervisor en su propia sesión. Si no, se aprobó en sitio, en la sesión de otro. */
    public boolean fromOwnAccount() {
        return idSupervisor.equals(idOperador);
    }

    /** Retira la aprobación sin borrarla. Retirar dos veces no emite dos eventos. */
    public void withdraw() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new WorkOrderApprovalWithdrawnEvent(
                metadataFor(WorkOrderApprovalWithdrawnEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "WorkOrderApproval";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private WorkOrderApprovalPayload payload() {
        return new WorkOrderApprovalPayload(idOrdenTrabajo, idSupervisor, fromOwnAccount());
    }
}
