package com.malphasos.malphasos.workorder.application.services.approval;

import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Desde qué día una orden ejecutada espera aprobación (2026-11-22, decidido por el usuario).
 *
 * <p>La carga de datos trae el historial de otro sistema como órdenes ejecutadas, sin aprobación: ese
 * sistema no la tenía y nadie va a aprobarlas. Sin un corte, todo el historial sale «por aprobar» —1158
 * órdenes en la base de desarrollo—. No hay columna que distinga lo cargado, a propósito: la carga no
 * cambia el esquema. Así que el corte es una fecha de configuración, {@code app.approval.since}: el día
 * en que la aprobación empezó a usarse en esa instalación —en desarrollo, el 2026-11-02, cuando entró
 * {@code V54}; en un servidor nuevo, el día que se pone en marcha—.
 *
 * <p>Sin fecha, no hay corte: toda ejecutada sin aprobar espera. Una fecha mal escrita tumba el arranque,
 * que es mejor que un corte que no corta.
 */
@Component
public class ApprovalStart {

    private final LocalDate desde;

    public ApprovalStart(@Value("${app.approval.since:}") String desde) {
        this.desde = desde == null || desde.isBlank() ? null : LocalDate.parse(desde.strip());
    }

    /** El día del corte, o vacío si no se configuró. */
    public Optional<LocalDate> desde() {
        return Optional.ofNullable(desde);
    }

    /**
     * Si una orden ejecutada espera aprobación: la ejecutada desde el corte. El día de ejecución es el de
     * su documento; las congeladas tarde no lo saben, y cuenta el programado.
     */
    public boolean awaits(WorkOrder orden) {
        if (desde == null) {
            return true;
        }
        LocalDate ejecutada = orden.getDocumento() != null && orden.getDocumento().fechaEjecucion() != null
                ? orden.getDocumento().fechaEjecucion()
                : orden.getFechaMantenimiento();

        return ejecutada == null || !ejecutada.isBefore(desde);
    }
}
