package com.malphasos.malphasos.report.application.services.serviceReport;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.OpenServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.VerificationReadingCommand;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los reportes de servicio.
 *
 * <p>Aquí viven las <b>reglas cruzadas</b> que ni el esquema ni el agregado pueden defender. El
 * esquema sostiene una sola —que el equipo esté en el alcance de la orden, con una clave foránea
 * compuesta contra el puente—, y ni siquiera esa por completo: no puede ver si esa fila del puente
 * sigue activa. El agregado solo decide con lo que el propio reporte tiene delante.
 *
 * <p><b>Este módulo consulta a la orden de trabajo, y es el primero que lo hace.</b> Hasta ahora
 * {@code work-order} era el módulo con más vecinos y nadie lo consultaba a él. Sigue sin haber
 * ciclos: reporte → orden → cliente, equipo y persona.
 *
 * <p><b>Para saber cómo se verifica un equipo hay que caminar cuatro eslabones</b> —unidad → modelo →
 * equipo del catálogo → tipo—, porque así es la cadena del catálogo. Se camina en
 * {@link #tipoDelEquipo(UUID)} y en un solo sitio, para que el coste esté a la vista y no repartido.
 */
@Service
@RequiredArgsConstructor
public class ServiceReportService implements ServiceReportServicePort {

    private final ServiceReportPersistencePort serviceReportPersistencePort;
    private final WorkOrderServicePort workOrderServicePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final ModelServicePort modelServicePort;
    private final EquipmentServicePort equipmentServicePort;
    private final EquipmentTypeServicePort equipmentTypeServicePort;
    private final EventDispatcherPort eventDispatcherPort;

    // ---------------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo) {
        workOrderServicePort.findById(idOrdenTrabajo);

        return serviceReportPersistencePort.findByWorkOrder(idOrdenTrabajo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByEquipment(UUID idEquipoCliente) {
        clientEquipmentServicePort.findById(idEquipoCliente);

        return serviceReportPersistencePort.findByEquipment(idEquipoCliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceReport findById(UUID id) {
        return serviceReportPersistencePort.findById(id)
                .orElseThrow(() -> new ServiceReportNotFoundException(id));
    }

    // ---------------------------------------------------------------------------
    // Alta
    // ---------------------------------------------------------------------------

    /**
     * Abre el reporte de un equipo de una orden, tras comprobar las tres cosas que el esquema no
     * puede.
     *
     * <p><b>Que la orden haya empezado</b>: un reporte cuenta lo que se hizo, y en una orden todavía
     * en {@code CREADA} no se ha hecho nada. <b>Que el equipo siga en el alcance</b>: la clave
     * foránea confirma que la fila del puente existe, no que siga activa, y un equipo retirado del
     * alcance dejó de estar en esa orden. <b>Que no haya ya un reporte vivo</b>: lo impide también el
     * índice único parcial, pero comprobarlo aquí convierte un conflicto de integridad en una frase.
     *
     * <p>Una orden ya {@code EJECUTADA} sí admite abrir reportes, y es deliberado: en este negocio se
     * va a la sede y se registra después. Lo que no se admite es reportar antes de empezar.
     */
    @Override
    @Transactional
    public ServiceReport open(OpenServiceReportCommand command) {
        WorkOrder orden = workOrderServicePort.findById(command.idOrdenTrabajo());

        requireStartedOrder(orden);
        requireEquipmentInScopeOf(orden, command.idEquipoCliente());
        requireNoOpenReport(command.idOrdenTrabajo(), command.idEquipoCliente());

        return persistAndPublish(
                ServiceReport.open(command.idOrdenTrabajo(), command.idEquipoCliente()));
    }

    // ---------------------------------------------------------------------------
    // Contenido
    // ---------------------------------------------------------------------------

    @Override
    @Transactional
    public ServiceReport fill(FillServiceReportCommand command) {
        ServiceReport reporte = findById(command.id());

        reporte.fill(
                command.fallaReportada(),
                command.diagnostico(),
                command.procedimientos(),
                command.observaciones(),
                command.resultado());

        return persistAndPublish(reporte);
    }

    /**
     * Registra las lecturas, después de comprobarlas contra la configuración del tipo de equipo.
     *
     * <p>Es la regla que justifica el módulo de reportes hablando con el de equipos: el tipo declara
     * <b>cómo</b> se verifica —modalidad, puntos y cuántas lecturas por punto— y aquí se comprueba que
     * lo registrado encaje. Sin esto cabría un reporte con lecturas en un punto que ese tipo nunca
     * declaró, o con veinte lecturas donde el tipo pide tres.
     *
     * <p>La unidad no se acepta del llamante cuando hay punto: se copia del punto. Ver
     * {@link VerificationReadingCommand}.
     */
    @Override
    @Transactional
    public ServiceReport recordVerification(RecordVerificationCommand command) {
        ServiceReport reporte = findById(command.id());
        EquipmentType tipo = tipoDelEquipo(reporte.getIdEquipoCliente());

        reporte.recordVerification(traducirLecturas(tipo, command.lecturas()));

        return persistAndPublish(reporte);
    }

    // ---------------------------------------------------------------------------
    // Cierre
    // ---------------------------------------------------------------------------

    /**
     * Cierra el reporte tras comprobar que la verificación está completa.
     *
     * <p>El agregado ya exige procedimientos y resultado. Lo que se añade aquí es lo que solo se
     * sabe preguntando al tipo de equipo: si ese tipo se verifica, el reporte no se cierra con la
     * tabla a medias, porque un certificado con dos de las tres lecturas no vale para nada.
     */
    @Override
    @Transactional
    public ServiceReport finish(FinishServiceReportCommand command) {
        ServiceReport reporte = findById(command.id());

        requireCompleteVerification(reporte);
        reporte.finish();

        return persistAndPublish(reporte);
    }

    @Override
    @Transactional
    public void discard(DiscardServiceReportCommand command) {
        ServiceReport reporte = findById(command.id());
        reporte.deactivate();

        persistAndPublish(reporte);
    }

    // ---------------------------------------------------------------------------

    /**
     * Persiste y publica lo que el agregado decidió que ocurrió.
     *
     * <p>Se publican los eventos del agregado recibido y no los del devuelto por el almacén: el
     * segundo se rehidrata y por eso viene sin ninguno.
     */
    private ServiceReport persistAndPublish(ServiceReport reporte) {
        ServiceReport guardado = serviceReportPersistencePort.save(reporte);
        reporte.pullEvents().forEach(eventDispatcherPort::dispatch);

        return guardado;
    }

    private void requireStartedOrder(WorkOrder orden) {
        if (!orden.isEstadoActivo()) {
            throw new IllegalStateException(
                    "No se abre un reporte en una orden cancelada: " + orden.getId());
        }
        if (orden.getEstadoEjecucion() == ExecutionState.CREADA) {
            throw new IllegalStateException("La orden " + orden.getId() + " no ha empezado:"
                    + " un reporte cuenta lo que se hizo, y todavia no se ha hecho nada");
        }
    }

    /**
     * El equipo está en el alcance <b>vivo</b> de la orden.
     *
     * <p>La clave foránea compuesta del esquema ya impide reportar un equipo que la orden nunca
     * incluyó. Lo que no puede ver es el borrado lógico: un equipo retirado del alcance conserva su
     * fila, de modo que para la base sigue siendo un destino válido. Es la misma distinción entre
     * «existe» y «sigue en uso» que ya subió reglas al servicio en {@code client}, en
     * {@code equipment} y en {@code work-order}.
     */
    private void requireEquipmentInScopeOf(WorkOrder orden, UUID idEquipoCliente) {
        boolean enElAlcance = orden.getEquipos().stream()
                .map(SelectedEquipment::getIdEquipoCliente)
                .anyMatch(idEquipoCliente::equals);

        if (!enElAlcance) {
            throw new IllegalArgumentException("El equipo " + idEquipoCliente
                    + " no esta en el alcance de la orden " + orden.getId());
        }
    }

    private void requireNoOpenReport(UUID idOrdenTrabajo, UUID idEquipoCliente) {
        serviceReportPersistencePort
                .findActiveByWorkOrderAndEquipment(idOrdenTrabajo, idEquipoCliente)
                .ifPresent(abierto -> {
                    throw new IllegalStateException("El equipo " + idEquipoCliente
                            + " ya tiene el reporte " + abierto.getId() + " en esta orden:"
                            + " para volver a empezar hay que retirar ese");
                });
    }

    /**
     * Traduce las lecturas recibidas y las comprueba contra la configuración del tipo.
     *
     * <p>Cuatro reglas, y cada una tapa un reporte que de otro modo saldría impreso diciendo algo
     * falso: que el tipo se verifique, que la lectura señale un punto <b>activo de ese tipo</b> —o
     * ninguno, si patrón y equipo varían—, que su número no pase de las lecturas que el tipo declara,
     * y que la unidad sea la del punto y no la que venga escrita.
     */
    private List<VerificationReading> traducirLecturas(
            EquipmentType tipo, List<VerificationReadingCommand> lecturas) {

        if (!tipo.isVerificable()) {
            throw new IllegalArgumentException("El tipo de equipo " + tipo.getNombre()
                    + " no se verifica: no hay lecturas que registrarle");
        }
        if (lecturas == null || lecturas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Registrar una verificacion sin lecturas no es registrar nada");
        }

        boolean variable = tipo.getModalidadVerificacion() == VerificationMode.PATRON_EQUIPO_VARIABLE;

        return lecturas.stream().map(lectura -> variable
                        ? sinPunto(tipo, lectura)
                        : enPunto(tipo, lectura))
                .toList();
    }

    /** Con patrón y equipo variables no hay punto, y la unidad tiene que venir de fuera. */
    private VerificationReading sinPunto(EquipmentType tipo, VerificationReadingCommand lectura) {
        if (lectura.idPuntoVerificacion() != null) {
            throw new IllegalArgumentException("El tipo " + tipo.getNombre()
                    + " se verifica con patron y equipo variables: no tiene puntos, y se recibio el "
                    + lectura.idPuntoVerificacion());
        }

        return VerificationReading.of(null, lectura.secuencia(), lectura.valorPatron(),
                lectura.valorEquipo(), lectura.unidadSinPunto());
    }

    /** Con una modalidad constante toda lectura se toma en un punto declarado del tipo. */
    private VerificationReading enPunto(EquipmentType tipo, VerificationReadingCommand lectura) {
        if (lectura.idPuntoVerificacion() == null) {
            throw new IllegalArgumentException("El tipo " + tipo.getNombre() + " se verifica en "
                    + tipo.puntosActivos().size() + " puntos: toda lectura declara en cual se tomo");
        }

        VerificationPoint punto = tipo.puntosActivos().stream()
                .filter(activo -> activo.id().equals(lectura.idPuntoVerificacion()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El punto "
                        + lectura.idPuntoVerificacion() + " no es un punto activo del tipo "
                        + tipo.getNombre()));

        if (lectura.secuencia() > tipo.getCantidadDatos()) {
            throw new IllegalArgumentException("El tipo " + tipo.getNombre() + " declara "
                    + tipo.getCantidadDatos() + " lecturas por punto, y se recibio la numero "
                    + lectura.secuencia());
        }

        // La unidad sale del punto, no del comando: asi no puede contradecirlo.
        return VerificationReading.of(punto.id(), lectura.secuencia(), lectura.valorPatron(),
                lectura.valorEquipo(), punto.unidad());
    }

    /**
     * La verificación está completa, o el equipo quedó fuera de servicio.
     *
     * <p><b>La excepción no es una concesión, es el caso normal de un equipo averiado</b>: a un
     * equipo que no enciende no se le puede tomar una lectura, y exigirlas obligaría a inventárselas
     * para poder cerrar el reporte que precisamente dice que está fuera de servicio.
     *
     * <p>Con una modalidad constante se exigen todas las lecturas de todos los puntos activos; con
     * patrón y equipo variables, al menos una, porque cuántas tomar lo decide el ingeniero.
     */
    private void requireCompleteVerification(ServiceReport reporte) {
        EquipmentType tipo = tipoDelEquipo(reporte.getIdEquipoCliente());

        if (!tipo.isVerificable() || reporte.getResultado() == ServiceResult.FUERA_DE_SERVICIO) {
            return;
        }

        List<VerificationReading> tomadas = reporte.lecturasActivas();

        if (tipo.getModalidadVerificacion() == VerificationMode.PATRON_EQUIPO_VARIABLE) {
            if (tomadas.isEmpty()) {
                throw new IllegalStateException("El tipo " + tipo.getNombre()
                        + " se verifica, y este reporte no tiene ninguna lectura");
            }

            return;
        }

        for (VerificationPoint punto : tipo.puntosActivos()) {
            long enEsePunto = tomadas.stream()
                    .filter(lectura -> Objects.equals(lectura.idPuntoVerificacion(), punto.id()))
                    .count();

            if (enEsePunto < tipo.getCantidadDatos()) {
                throw new IllegalStateException("El punto " + punto.valor() + " " + punto.unidad()
                        + " tiene " + enEsePunto + " de las " + tipo.getCantidadDatos()
                        + " lecturas que el tipo " + tipo.getNombre() + " declara");
            }
        }
    }

    /**
     * El tipo de equipo de una unidad, caminando la cadena del catálogo.
     *
     * <p>Cuatro consultas: unidad → modelo → equipo del catálogo → tipo. Así está construido el
     * catálogo —«equipo» es a la vez categoría y máquina— y ninguna de las tres tablas intermedias
     * guarda un atajo hacia el tipo. Se deja en un solo método para que el coste sea visible y para
     * que el día que haga falta un atajo se sepa exactamente dónde ponerlo.
     */
    private EquipmentType tipoDelEquipo(UUID idEquipoCliente) {
        ClientEquipment unidad = clientEquipmentServicePort.findById(idEquipoCliente);
        UUID idEquipoCatalogo = modelServicePort.findById(unidad.getIdModelo()).getIdEquipo();
        UUID idTipo = equipmentServicePort.findById(idEquipoCatalogo).getIdTipoEquipo();

        return equipmentTypeServicePort.findById(idTipo);
    }
}
