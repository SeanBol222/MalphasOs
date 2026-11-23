package com.malphasos.malphasos.equipment.domain.externalIntervention;

import com.malphasos.malphasos.equipment.domain.externalIntervention.events.ExternalInterventionDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.externalIntervention.events.ExternalInterventionPayload;
import com.malphasos.malphasos.equipment.domain.externalIntervention.events.ExternalInterventionRecordedEvent;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Algo que se le hizo a un equipo fuera de MalphasOS (V61, 2026-11-23): un mantenimiento, una calibración u
 * otra cosa de otra empresa, o de antes de usar el sistema. Es una línea más del historial de su hoja de
 * vida, que la ordena por fecha con las de los reportes.
 *
 * <p>Decidido por el usuario: <b>pocos datos, para que sea rápido de meter</b> —cuándo, qué tipo, qué se hizo
 * y qué empresa—; opcionales, cómo quedó el equipo y el número del certificado o informe del tercero. Sin
 * adjunto. No se edita: una mal anotada se retira y se anota otra, como un reporte cerrado se sustituye.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExternalIntervention extends AggregateRoot {

    static final int LARGO_DESCRIPCION = 500;
    static final int LARGO_EMPRESA = 120;
    static final int LARGO_DOCUMENTO = 80;

    @EqualsAndHashCode.Include
    private final UUID id;

    private final UUID idEquipoCliente;

    /** El día en que se hizo, no el día en que se anotó. */
    private final LocalDate realizada;

    private final ExternalServiceType tipoServicio;

    private final String descripcion;

    private final String empresa;

    /** Cómo quedó el equipo, si se sabe; nulo si no. */
    private final InterventionResult resultado;

    /** El número del certificado o informe del tercero, si lo hay. */
    private final String documento;

    /** Cuándo se anotó en MalphasOS: nulo hasta que lo guarda la base. */
    private final LocalDateTime registrada;

    private boolean estadoActivo;

    private ExternalIntervention(
            UUID id, UUID idEquipoCliente, LocalDate realizada, ExternalServiceType tipoServicio,
            String descripcion, String empresa, InterventionResult resultado, String documento,
            LocalDateTime registrada, boolean estadoActivo) {
        this.id = id;
        this.idEquipoCliente = idEquipoCliente;
        this.realizada = realizada;
        this.tipoServicio = tipoServicio;
        this.descripcion = descripcion;
        this.empresa = empresa;
        this.resultado = resultado;
        this.documento = documento;
        this.registrada = registrada;
        this.estadoActivo = estadoActivo;
    }

    /**
     * @param hoy el día de hoy en la zona de la empresa: lo que pasó no puede ser de mañana
     */
    public static ExternalIntervention create(
            UUID idEquipoCliente, LocalDate realizada, ExternalServiceType tipoServicio, String descripcion,
            String empresa, InterventionResult resultado, String documento, LocalDate hoy) {
        if (idEquipoCliente == null) {
            throw new IllegalArgumentException("Una intervencion externa necesita el equipo");
        }
        if (realizada == null) {
            throw new IllegalArgumentException("Una intervencion externa necesita la fecha en que se hizo");
        }
        if (realizada.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha de una intervencion externa no puede ser futura");
        }
        if (tipoServicio == null) {
            throw new IllegalArgumentException("Una intervencion externa necesita el tipo de servicio");
        }

        ExternalIntervention intervencion = new ExternalIntervention(UUID.randomUUID(), idEquipoCliente, realizada,
                tipoServicio, obligatorio(descripcion, "que se hizo", LARGO_DESCRIPCION),
                obligatorio(empresa, "la empresa que lo hizo", LARGO_EMPRESA), resultado,
                opcional(documento, "el documento", LARGO_DOCUMENTO), null, true);
        intervencion.registerEvent(new ExternalInterventionRecordedEvent(
                intervencion.metadataFor(ExternalInterventionRecordedEvent.TYPE), intervencion.payload()));

        return intervencion;
    }

    public static ExternalIntervention rehydrate(
            UUID id, UUID idEquipoCliente, LocalDate realizada, ExternalServiceType tipoServicio,
            String descripcion, String empresa, InterventionResult resultado, String documento,
            LocalDateTime registrada, boolean estadoActivo) {
        return new ExternalIntervention(id, idEquipoCliente, realizada, tipoServicio, descripcion, empresa,
                resultado, documento, registrada, estadoActivo);
    }

    /** La retira del historial sin borrarla. Retirarla dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ExternalInterventionDeactivatedEvent(
                metadataFor(ExternalInterventionDeactivatedEvent.TYPE), payload()));
    }

    private ExternalInterventionPayload payload() {
        return new ExternalInterventionPayload(idEquipoCliente, realizada, tipoServicio, empresa);
    }

    @Override
    protected String aggregateType() {
        return "ExternalIntervention";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private static String obligatorio(String valor, String campo, int largo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Una intervencion externa necesita " + campo);
        }
        return acotado(valor.strip(), campo, largo);
    }

    private static String opcional(String valor, String campo, int largo) {
        return valor == null || valor.isBlank() ? null : acotado(valor.strip(), campo, largo);
    }

    private static String acotado(String valor, String campo, int largo) {
        if (valor.length() > largo) {
            throw new IllegalArgumentException(
                    "En una intervencion externa, " + campo + " admite hasta " + largo + " caracteres");
        }
        return valor;
    }
}
