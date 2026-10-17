package com.malphasos.malphasos.report.domain.serviceReport;

import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportDeactivatedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFilledEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportHandedOverEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportIntakeRecordedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportOpenedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportPayload;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStandardChosenEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStandardRemovedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportStepMarkedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportVerifiedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Lo que se hizo sobre un equipo concreto de una orden de trabajo.
 *
 * <p>La orden dice <b>qué se va a hacer</b>; este agregado dice <b>qué se encontró, qué se hizo y cómo
 * quedó el equipo</b>. Hay <b>uno por equipo de la orden</b>, no uno por orden: lo pide RF-09 con esas
 * palabras y es lo que ocurre en campo, donde cada equipo se interviene y se entrega por separado.
 *
 * <p><b>Nace vacío.</b> Los cinco campos de RF-15 —falla reportada, diagnóstico, procedimientos,
 * observaciones y resultado— son opcionales mientras el reporte está en {@link ReportState#BORRADOR},
 * porque el reporte se abre al llegar al equipo y se llena allí. Al cerrarlo se exigen dos:
 * procedimientos y resultado, que son qué se hizo y cómo quedó. Falla y diagnóstico se quedan
 * opcionales incluso al cerrar, porque un preventivo que sale bien no tiene ninguna de las dos.
 *
 * <p><b>No copia nada de la orden.</b> Cliente, sede, tipo de servicio y responsables se consultan por
 * el identificador de la orden, que es lo que RF-11 llama autocompletar. La orden ya los congeló al
 * crearse; una tercera copia habría que mantenerla de acuerdo con las otras dos.
 *
 * <p><b>Lo que este agregado no puede comprobar</b>, y por eso vive en el servicio de aplicación: que
 * la orden exista y esté activa, que el equipo esté en su alcance, que el tipo de ese equipo se
 * verifique de la forma en que dicen las lecturas, y que cada punto pertenezca a ese tipo. Todo eso
 * exige consultar otros módulos. Aquí solo se defiende lo que se decide con lo que el reporte tiene
 * delante.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ServiceReport extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    /** Orden de la que cuelga. No cambia: un reporte no se muda de orden. */
    private final UUID idOrdenTrabajo;

    /** Equipo del que habla. Tampoco cambia. */
    private final UUID idEquipoCliente;

    private ReportState estado;

    private String fallaReportada;

    private String diagnostico;

    private String procedimientos;

    private String observaciones;

    /** Por qué falló, según el ingeniero. Sobre todo de un correctivo; desde V24. */
    private String causa;

    /** Repuestos cambiados, en texto libre. Desde V24. */
    private String repuestos;

    /**
     * Contra qué patrones se tomaron las lecturas de cada verificación, copiados como estaban ese día,
     * retirados incluidos. Desde V30 (2026-10-16); con V27 era un solo patrón por reporte.
     */
    private final List<VerificationStandard> patrones = new ArrayList<>();

    /** Cómo llegó la estructura física del equipo. Nulo mientras no se registre. Desde V31. */
    private IntakeCheck ingresoFisico;

    /** Cómo llegó funcionando el equipo. Nulo mientras no se registre. Desde V31. */
    private IntakeCheck ingresoOperativo;

    /** Los accesorios con que llegó, retirados incluidos, como las lecturas. Desde V31. */
    private final List<ReportAccessory> accesorios = new ArrayList<>();

    /** Quién recibió el equipo al entregarlo. Nulo mientras no se diga. Desde V31. */
    private Handover recibe;

    private ServiceResult resultado;

    /** Cuándo se cerró, que es cuándo se prestó el servicio de verdad. Nulo en borrador. */
    private LocalDateTime finalizado;

    /**
     * Las lecturas de la verificación, retiradas incluidas.
     *
     * <p>Se conservan las retiradas por lo mismo que en el tipo de equipo: aquí nada se borra, y una
     * lectura corregida sigue formando parte de lo que pasó.
     */
    private final List<VerificationReading> lecturas = new ArrayList<>();

    /**
     * Los pasos del protocolo preventivo, copiados al abrir el reporte y en su orden. Vacía en un
     * correctivo, en un tipo sin protocolos y en los reportes abiertos antes de V22.
     */
    private final List<ReportStep> pasos = new ArrayList<>();

    private boolean estadoActivo;

    /** Lo que cabe en la descripción de una intervención de la hoja de vida: {@code varchar(500)}. */
    static final int LARGO_DESCRIPCION_DEL_SERVICIO = 500;

    private ServiceReport(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idEquipoCliente,
            ReportState estado,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            ServiceResult resultado,
            LocalDateTime finalizado,
            Collection<VerificationReading> lecturas,
            Collection<ReportStep> pasos,
            boolean estadoActivo) {

        this.id = id;
        this.idOrdenTrabajo = idOrdenTrabajo;
        this.idEquipoCliente = idEquipoCliente;
        this.estado = estado;
        this.fallaReportada = fallaReportada;
        this.diagnostico = diagnostico;
        this.procedimientos = procedimientos;
        this.observaciones = observaciones;
        this.resultado = resultado;
        this.finalizado = finalizado;
        this.estadoActivo = estadoActivo;

        if (lecturas != null) {
            this.lecturas.addAll(lecturas);
        }
        if (pasos != null) {
            pasos.stream()
                    .sorted((a, b) -> Integer.compare(a.orden(), b.orden()))
                    .forEach(this.pasos::add);
        }
    }

    /** Cuántos caracteres del identificador forman el número del reporte (V28). */
    public static final int LARGO_NUMERO = 6;

    /**
     * El número con que se cita el reporte: los seis primeros caracteres de su identificador, en
     * mayúsculas, como un commit de git (decidido por el usuario el 2026-10-16). Único: lo garantiza el
     * índice de V28 y quien abre el reporte elige otro identificador si el prefijo está tomado.
     */
    public String numero() {
        return numeroDe(id);
    }

    public static String numeroDe(UUID id) {
        return id.toString().substring(0, LARGO_NUMERO).toUpperCase(java.util.Locale.ROOT);
    }

    /** Abre el reporte de un equipo de una orden, sin pasos de protocolo: un correctivo. */
    public static ServiceReport open(UUID idOrdenTrabajo, UUID idEquipoCliente) {
        return open(idOrdenTrabajo, idEquipoCliente, List.of());
    }

    /**
     * Abre el reporte de un equipo de una orden. Nace en borrador, vacío y sin lecturas, con los pasos
     * del protocolo que se le den —los del preventivo del tipo del equipo— pendientes y en su orden.
     *
     * <p>Qué pasos le tocan lo decide el servicio, que es quien sabe el tipo de servicio de la orden y
     * el tipo del equipo. Aquí solo se exige que no se repita un orden.
     */
    public static ServiceReport open(UUID idOrdenTrabajo, UUID idEquipoCliente, List<ReportStep> pasos) {
        List<ReportStep> copiados = pasos == null ? List.of() : List.copyOf(pasos);

        if (copiados.stream().map(ReportStep::orden).distinct().count() != copiados.size()) {
            throw new IllegalArgumentException("Dos pasos del protocolo no pueden tener el mismo orden");
        }

        ServiceReport reporte = new ServiceReport(
                UUID.randomUUID(),
                exigir(idOrdenTrabajo, "orden de trabajo"),
                exigir(idEquipoCliente, "equipo"),
                ReportState.BORRADOR,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                copiados,
                true);

        reporte.registerEvent(new ServiceReportOpenedEvent(
                reporte.metadataFor(ServiceReportOpenedEvent.TYPE), reporte.payload()));

        return reporte;
    }

    public static ServiceReport rehydrate(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idEquipoCliente,
            ReportState estado,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            ServiceResult resultado,
            LocalDateTime finalizado,
            Collection<VerificationReading> lecturas,
            boolean estadoActivo) {

        return rehydrate(id, idOrdenTrabajo, idEquipoCliente, estado, fallaReportada, diagnostico,
                procedimientos, observaciones, resultado, finalizado, lecturas, List.of(), estadoActivo);
    }

    public static ServiceReport rehydrate(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idEquipoCliente,
            ReportState estado,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            ServiceResult resultado,
            LocalDateTime finalizado,
            Collection<VerificationReading> lecturas,
            Collection<ReportStep> pasos,
            boolean estadoActivo) {

        return rehydrate(id, idOrdenTrabajo, idEquipoCliente, estado, fallaReportada, diagnostico,
                procedimientos, observaciones, null, null, resultado, finalizado, lecturas, pasos,
                estadoActivo);
    }

    /** Con la causa y los repuestos de V24, sin el patrón de V27. */
    public static ServiceReport rehydrate(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idEquipoCliente,
            ReportState estado,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            String causa,
            String repuestos,
            ServiceResult resultado,
            LocalDateTime finalizado,
            Collection<VerificationReading> lecturas,
            Collection<ReportStep> pasos,
            boolean estadoActivo) {

        return rehydrate(id, idOrdenTrabajo, idEquipoCliente, estado, fallaReportada, diagnostico,
                procedimientos, observaciones, causa, repuestos, null, resultado, finalizado, lecturas, pasos,
                estadoActivo);
    }

    /** Con la causa y los repuestos de V24 y los patrones de V30, sin lo de V31. */
    public static ServiceReport rehydrate(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idEquipoCliente,
            ReportState estado,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            String causa,
            String repuestos,
            Collection<VerificationStandard> patrones,
            ServiceResult resultado,
            LocalDateTime finalizado,
            Collection<VerificationReading> lecturas,
            Collection<ReportStep> pasos,
            boolean estadoActivo) {

        return rehydrate(id, idOrdenTrabajo, idEquipoCliente, estado, fallaReportada, diagnostico,
                procedimientos, observaciones, causa, repuestos, patrones, null, null, List.of(), null, resultado,
                finalizado, lecturas, pasos, estadoActivo);
    }

    /**
     * La rehidratación completa: con la causa y los repuestos de V24, los patrones de V30 y el ingreso,
     * los accesorios y quién recibe de V31.
     */
    public static ServiceReport rehydrate(
            UUID id,
            UUID idOrdenTrabajo,
            UUID idEquipoCliente,
            ReportState estado,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            String causa,
            String repuestos,
            Collection<VerificationStandard> patrones,
            IntakeCheck ingresoFisico,
            IntakeCheck ingresoOperativo,
            Collection<ReportAccessory> accesorios,
            Handover recibe,
            ServiceResult resultado,
            LocalDateTime finalizado,
            Collection<VerificationReading> lecturas,
            Collection<ReportStep> pasos,
            boolean estadoActivo) {

        // Sin validar: leer de la base no es un hecho del dominio, y una fila vieja que ya no cumple una
        // regla nueva tiene que poder cargarse para poder corregirla.
        ServiceReport reporte = new ServiceReport(id, idOrdenTrabajo, idEquipoCliente, estado, fallaReportada,
                diagnostico, procedimientos, observaciones, resultado, finalizado, lecturas, pasos,
                estadoActivo);
        reporte.causa = causa;
        reporte.repuestos = repuestos;
        if (patrones != null) {
            reporte.patrones.addAll(patrones);
        }
        reporte.ingresoFisico = ingresoFisico;
        reporte.ingresoOperativo = ingresoOperativo;
        if (accesorios != null) {
            reporte.accesorios.addAll(accesorios);
        }
        reporte.recibe = recibe;

        return reporte;
    }

    // ---------------------------------------------------------------------------
    // El cuerpo del reporte: RF-15
    // ---------------------------------------------------------------------------

    /**
     * Registra o corrige la información técnica del reporte.
     *
     * <p><b>Un nulo deja el campo como está y un texto en blanco lo borra</b>, que son dos cosas
     * distintas y las dos hacen falta: el formulario manda solo lo que se tocó, y un campo que se
     * escribió por error tiene que poder vaciarse. La misma distinción no existe para el resultado,
     * que es una enumeración: ahí un nulo solo puede significar «no lo cambies». Quitar un resultado
     * puesto por error no está previsto, y tampoco hace falta, porque cerrar exige uno.
     *
     * <p>Un cambio que no cambia nada no emite evento.
     */
    public void fill(
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            ServiceResult resultado) {

        fill(fallaReportada, diagnostico, procedimientos, observaciones, null, null, resultado);
    }

    /**
     * Lo mismo, con la causa y los repuestos de un correctivo (V24, 2026-10-14). Mismas reglas: un nulo
     * no cambia nada y un blanco borra.
     */
    public void fill(
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            String causa,
            String repuestos,
            ServiceResult resultado) {

        exigirModificable("llenar el reporte");

        boolean cambio = false;

        if (fallaReportada != null && !Objects.equals(normalizar(fallaReportada), this.fallaReportada)) {
            this.fallaReportada = normalizar(fallaReportada);
            cambio = true;
        }
        if (diagnostico != null && !Objects.equals(normalizar(diagnostico), this.diagnostico)) {
            this.diagnostico = normalizar(diagnostico);
            cambio = true;
        }
        if (procedimientos != null && !Objects.equals(normalizar(procedimientos), this.procedimientos)) {
            this.procedimientos = normalizar(procedimientos);
            cambio = true;
        }
        if (observaciones != null && !Objects.equals(normalizar(observaciones), this.observaciones)) {
            this.observaciones = normalizar(observaciones);
            cambio = true;
        }
        if (causa != null && !Objects.equals(normalizar(causa), this.causa)) {
            this.causa = normalizar(causa);
            cambio = true;
        }
        if (repuestos != null && !Objects.equals(normalizar(repuestos), this.repuestos)) {
            this.repuestos = normalizar(repuestos);
            cambio = true;
        }
        if (resultado != null && resultado != this.resultado) {
            this.resultado = resultado;
            cambio = true;
        }

        if (cambio) {
            registerEvent(new ServiceReportFilledEvent(
                    metadataFor(ServiceReportFilledEvent.TYPE), payload()));
        }
    }

    // ---------------------------------------------------------------------------
    // La recepción y la entrega: V31
    // ---------------------------------------------------------------------------

    /**
     * Registra cómo llegó el equipo: las dos verificaciones al ingreso y sus accesorios, que sustituyen a
     * los que hubiera (V31). Van juntos porque se miran juntos, al recibir el equipo y antes de tocarlo.
     *
     * <p>Las dos verificaciones son obligatorias —son las dos filas fijas del informe— y los accesorios
     * pueden ser ninguno: muchos equipos llegan solos. Los anteriores se retiran, no se borran.
     * Registrar exactamente lo mismo no emite evento.
     */
    public void recordIntake(IntakeCheck fisico, IntakeCheck operativo, Collection<ReportAccessory> accesorios) {
        exigirModificable("registrar como llego el equipo");

        if (fisico == null || operativo == null) {
            throw new IllegalArgumentException(
                    "Hay que registrar las dos verificaciones al ingreso: estructura fisica y estado operativo");
        }

        List<ReportAccessory> nuevos = accesorios == null ? List.of() : List.copyOf(accesorios);
        if (nuevos.stream().map(ReportAccessory::orden).distinct().count() != nuevos.size()) {
            throw new IllegalArgumentException("Dos accesorios no pueden tener el mismo orden");
        }

        if (fisico.equals(ingresoFisico) && operativo.equals(ingresoOperativo) && mismosAccesorios(nuevos)) {
            return;
        }

        this.ingresoFisico = fisico;
        this.ingresoOperativo = operativo;
        this.accesorios.replaceAll(accesorio -> accesorio.estadoActivo() ? accesorio.deactivated() : accesorio);
        this.accesorios.addAll(nuevos);

        registerEvent(new ServiceReportIntakeRecordedEvent(
                metadataFor(ServiceReportIntakeRecordedEvent.TYPE), payload()));
    }

    /** Todos los accesorios, retirados incluidos, como copia inmutable. */
    public List<ReportAccessory> getAccesorios() {
        return List.copyOf(accesorios);
    }

    /** Los accesorios que valen hoy, en su orden. */
    public List<ReportAccessory> accesoriosActivos() {
        return accesorios.stream()
                .filter(ReportAccessory::estadoActivo)
                .sorted((a, b) -> Integer.compare(a.orden(), b.orden()))
                .toList();
    }

    /**
     * Registra quién recibió el equipo, o lo corrige; con nulo lo borra (V31). Decirlo otra vez igual no
     * emite evento.
     */
    public void recordHandover(Handover recibe) {
        exigirModificable("registrar quien recibe");

        if (Objects.equals(recibe, this.recibe)) {
            return;
        }

        this.recibe = recibe;
        registerEvent(new ServiceReportHandedOverEvent(
                metadataFor(ServiceReportHandedOverEvent.TYPE), payload()));
    }

    private boolean mismosAccesorios(List<ReportAccessory> pedidos) {
        List<ReportAccessory> activos = accesoriosActivos();

        return activos.size() == pedidos.size()
                && pedidos.stream().allMatch(pedido -> activos.stream().anyMatch(pedido::diceLoMismoQue));
    }

    // ---------------------------------------------------------------------------
    // La verificación metrológica: el resultado de medir
    // ---------------------------------------------------------------------------

    /**
     * Registra las lecturas de la verificación, sustituyendo las que hubiera.
     *
     * <p>Se sustituyen todas en vez de añadirse de una en una porque una verificación se toma y se
     * revisa como una tabla: el ingeniero corrige una celda y manda la tabla entera. Las anteriores se
     * retiran, no se borran. Registrar exactamente las mismas no emite evento.
     *
     * <p>Se rechazan dos lecturas que ocupen el mismo sitio —el mismo punto y el mismo número—, que es
     * lo mismo que impide el índice único de la tabla. Y se rechaza recibir la lista vacía: vaciar una
     * verificación no es registrarla, y si lo que se quiere es que no haya lecturas, el reporte nunca
     * debió registrarlas.
     */
    public void recordVerification(Collection<VerificationReading> lecturas) {
        exigirModificable("registrar la verificacion");

        List<VerificationReading> nuevas = validarLecturas(lecturas);

        if (mismasLecturas(nuevas)) {
            // Un cambio que no cambia nada no emite evento.
            return;
        }

        this.lecturas.replaceAll(lectura -> lectura.estadoActivo() ? lectura.deactivated() : lectura);
        this.lecturas.addAll(nuevas);

        registerEvent(new ServiceReportVerifiedEvent(
                metadataFor(ServiceReportVerifiedEvent.TYPE), payload()));
    }

    /**
     * Pone un patrón en una verificación del reporte (V30). Si ya estaba, refresca la copia con lo que
     * el catálogo dice hoy —renovar un certificado a mitad de servicio es justo el caso—; si estaba y se
     * quitó, lo reactiva. Ponerlo otra vez con lo mismo copiado no emite evento.
     *
     * <p>Que la verificación sea del tipo de este equipo lo comprueba el servicio, que es quien sabe el
     * tipo. Y aquí no se comprueba que esté vigente, a propósito: se comprueba al cerrar, contra el día
     * del cierre, que es el del servicio. Un patrón vigente al elegirlo puede vencer antes de que el
     * reporte se cierre, y al revés no importa.
     */
    public void useReferenceStandard(UUID idVerificacion, UsedReferenceStandard patron) {
        exigirModificable("elegir un patron");

        if (idVerificacion == null) {
            throw new IllegalArgumentException("Hay que decir en que verificacion se uso el patron");
        }
        if (patron == null) {
            throw new IllegalArgumentException("Hay que decir que patron se uso");
        }

        int indice = indiceDe(idVerificacion, patron.id());

        if (indice < 0) {
            patrones.add(VerificationStandard.of(idVerificacion, patron));
        } else {
            VerificationStandard actual = patrones.get(indice);
            if (actual.estadoActivo() && actual.patron().equals(patron)) {
                return;
            }
            patrones.set(indice, actual.withPatron(patron));
        }

        registerEvent(new ServiceReportStandardChosenEvent(
                metadataFor(ServiceReportStandardChosenEvent.TYPE), payload()));
    }

    /**
     * Quita un patrón de una verificación: lo retira, no lo borra. Quitar uno que no está no hace nada,
     * como retirar dos veces.
     */
    public void removeReferenceStandard(UUID idVerificacion, UUID idPatron) {
        exigirModificable("quitar un patron");

        int indice = indiceDe(idVerificacion, idPatron);

        if (indice < 0 || !patrones.get(indice).estadoActivo()) {
            return;
        }

        patrones.set(indice, patrones.get(indice).deactivated());
        registerEvent(new ServiceReportStandardRemovedEvent(
                metadataFor(ServiceReportStandardRemovedEvent.TYPE), payload()));
    }

    /** Todos los patrones de todas las verificaciones, retirados incluidos, como copia inmutable. */
    public List<VerificationStandard> getPatrones() {
        return List.copyOf(patrones);
    }

    /** Los patrones que valen hoy, en el orden en que se pusieron. */
    public List<VerificationStandard> patronesActivos() {
        return patrones.stream().filter(VerificationStandard::estadoActivo).toList();
    }

    /** Todas las lecturas, retiradas incluidas, como copia inmutable. */
    public List<VerificationReading> getLecturas() {
        return Collections.unmodifiableList(new ArrayList<>(lecturas));
    }

    /**
     * Las lecturas que valen hoy, en el orden en que se registraron.
     *
     * <p>No se ordenan por punto: aquí solo consta su identificador, y el valor por el que tendría
     * sentido ordenarlas —50, 100, 150 mmHg— vive en el tipo de equipo. Ordenar por él es tarea de
     * quien tiene los puntos delante.
     */
    public List<VerificationReading> lecturasActivas() {
        return lecturas.stream().filter(VerificationReading::estadoActivo).toList();
    }

    // ---------------------------------------------------------------------------
    // Los pasos del protocolo: RF-13
    // ---------------------------------------------------------------------------

    /** Los pasos del protocolo, en su orden, como copia inmutable. */
    public List<ReportStep> getPasos() {
        return List.copyOf(pasos);
    }

    /**
     * Marca un paso: hecho, no aplica —con nota que diga por qué— o de vuelta a pendiente.
     *
     * <p>De uno en uno y no la lista entera, porque en pantalla cada casilla se guarda al pulsarla. Un
     * cambio que no cambia nada no emite evento.
     */
    public void markStep(int orden, StepState estado, String nota) {
        exigirModificable("marcar un paso del protocolo");

        int indice = -1;
        for (int i = 0; i < pasos.size(); i++) {
            if (pasos.get(i).orden() == orden) {
                indice = i;
            }
        }
        if (indice < 0) {
            throw new IllegalArgumentException("Este reporte no tiene un paso " + orden + " en su protocolo");
        }

        ReportStep marcado = pasos.get(indice).marked(estado, nota);

        if (marcado.equals(pasos.get(indice))) {
            return;
        }

        pasos.set(indice, marcado);
        registerEvent(new ServiceReportStepMarkedEvent(
                metadataFor(ServiceReportStepMarkedEvent.TYPE), payload()));
    }

    /**
     * Lo que se hizo, en una línea, para la hoja de vida del equipo.
     *
     * <p>Decidido por el usuario el 2026-10-10: los nombres de los pasos hechos, en su orden, y detrás
     * el trabajo adicional que se escribió a mano. Los que no aplicaron no salen: no se hicieron. Sin
     * pasos es el texto escrito, como antes de V22. Si no cabe en la columna de la hoja de vida se
     * recorta con puntos suspensivos; el reporte conserva el detalle entero.
     */
    public String descripcionDelServicio() {
        List<String> hechos = pasos.stream()
                .filter(paso -> paso.estado() == StepState.HECHO)
                .map(ReportStep::nombre)
                .toList();

        String descripcion;
        if (hechos.isEmpty()) {
            descripcion = procedimientos;
        } else {
            descripcion = String.join("; ", hechos) + "."
                    + (procedimientos == null ? "" : " Además: " + procedimientos);
        }

        // Los repuestos cambiados se dicen en la hoja de vida: es lo que alguien busca cuando vuelve a
        // fallar la misma pieza. Desde V24.
        if (repuestos != null) {
            descripcion = (descripcion == null ? "" : descripcion + " ") + "Repuestos: " + repuestos + ".";
        }

        if (descripcion == null || descripcion.length() <= LARGO_DESCRIPCION_DEL_SERVICIO) {
            return descripcion;
        }

        return descripcion.substring(0, LARGO_DESCRIPCION_DEL_SERVICIO - 1) + "…";
    }

    // ---------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------

    /**
     * Cierra el reporte. Desde aquí ya no cambia.
     *
     * <p>Exige procedimientos y resultado: qué se hizo y cómo quedó el equipo. Sin lo primero el
     * reporte no cuenta nada, y sin lo segundo no se puede anotar en la hoja de vida ni disparar una
     * alerta. Es la misma pareja que exige el {@code CHECK} de cierre en V9, y está en los dos sitios a
     * propósito: el dominio da el mensaje y la base garantiza que nadie lo esquive por SQL.
     *
     * <p>Exige también las dos verificaciones al ingreso (decidido el 2026-10-16, con el diseño único del
     * informe). <b>Esta solo vive aquí</b>, sin {@code CHECK}: los reportes cerrados antes de V31 no las
     * tienen, y un {@code CHECK} —aun {@code NOT VALID}— se comprueba en todo {@code UPDATE}, de modo que
     * impediría retirarlos, que es la única salida de un reporte cerrado con un error. Quién recibe el
     * equipo no se exige, también por decisión del usuario: el informe deja su firma a mano.
     *
     * <p><b>Lo que no se comprueba aquí:</b> que un equipo verificable traiga sus lecturas. Saber si el
     * tipo de ese equipo se verifica exige consultar el módulo de equipos, y eso es del servicio.
     */
    public void finish() {
        exigirModificable("cerrar el reporte");

        // Con pasos, lo que se hizo lo dicen ellos y el texto es trabajo adicional, opcional. Sin pasos,
        // el texto es lo unico que cuenta que se hizo. Desde V22; antes se exigia siempre.
        long pendientes = pasos.stream().filter(paso -> !paso.marcado()).count();

        if (pendientes > 0) {
            throw new IllegalStateException("No se puede cerrar un reporte con " + pendientes
                    + (pendientes == 1 ? " paso" : " pasos") + " del protocolo sin marcar");
        }
        if (pasos.isEmpty() && procedimientos == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un reporte sin procedimientos: es lo que se hizo");
        }
        if (resultado == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un reporte sin resultado: es como quedo el equipo, y de ahi"
                            + " cuelgan la hoja de vida y las alertas");
        }
        if (ingresoFisico == null || ingresoOperativo == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un reporte sin las verificaciones al ingreso: son como llego el"
                            + " equipo, y sin ellas el informe no dice de donde se partio");
        }

        LocalDateTime ahora = LocalDateTime.now();
        exigirPatrones(ahora.toLocalDate());

        this.estado = ReportState.FINALIZADO;
        this.finalizado = ahora;
        registerEvent(new ServiceReportFinishedEvent(
                metadataFor(ServiceReportFinishedEvent.TYPE), payload()));
    }

    /**
     * Cierra el reporte de un correctivo: además de lo de todo reporte, exige la falla reportada y el
     * diagnóstico (decidido el 2026-10-14). Un correctivo que no dice qué falló ni qué se encontró no
     * cuenta qué se corrigió. Causa y repuestos no se exigen: no siempre se sabe la causa, y no
     * siempre se cambia algo.
     *
     * <p>Es un método aparte y no un parámetro de {@link #finish()} porque el reporte no sabe de qué tipo
     * de servicio es —lo dice la orden—: quien lo cierra elige cuál de los dos cierres aplica.
     */
    public void finishCorrective() {
        exigirModificable("cerrar el reporte");

        if (fallaReportada == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un correctivo sin la falla reportada: es lo que se vino a corregir");
        }
        if (diagnostico == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un correctivo sin diagnostico: es lo que se encontro");
        }

        finish();
    }

    /**
     * Retira el reporte sin borrarlo. Retirarlo dos veces no emite dos eventos.
     *
     * <p>Se permite retirar incluso uno cerrado, y es la única salida que tiene un reporte cerrado con
     * un error: se retira y se abre otro. Retirar no reescribe lo que ocurrió, solo lo saca del
     * listado; la fila se queda.
     */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new ServiceReportDeactivatedEvent(
                metadataFor(ServiceReportDeactivatedEvent.TYPE), payload()));
    }

    // ---------------------------------------------------------------------------

    @Override
    protected String aggregateType() {
        return "ServiceReport";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private ServiceReportPayload payload() {
        return new ServiceReportPayload(
                idOrdenTrabajo, idEquipoCliente, estado, resultado, finalizado);
    }

    /**
     * Los patrones, al cerrar (V27; por verificación desde V30). Cada verificación con lecturas dice
     * contra qué patrón se tomaron, porque unas lecturas sin patrón no dicen contra qué se compararon. Y
     * ninguno puede tener el certificado vencido el día del servicio, tenga lecturas su verificación o
     * no: el informe los imprime todos, y uno vencido no certifica nada.
     */
    private void exigirPatrones(LocalDate dia) {
        List<VerificationStandard> activos = patronesActivos();
        Set<UUID> medidas = new LinkedHashSet<>();
        lecturasActivas().forEach(lectura -> medidas.add(lectura.idVerificacion()));

        for (UUID verificacion : medidas) {
            if (activos.stream().noneMatch(usado -> usado.idVerificacion().equals(verificacion))) {
                throw new IllegalStateException("No se puede cerrar el reporte: la verificacion " + verificacion
                        + " tiene lecturas y no dice contra que patron se tomaron");
            }
        }
        for (VerificationStandard usado : activos) {
            UsedReferenceStandard patron = usado.patron();
            if (!patron.vigenteEl(dia)) {
                throw new IllegalStateException("No se puede cerrar el reporte: el certificado del patron "
                        + patron.nombre() + " (" + patron.certificado() + ") vencio el " + patron.vencimiento());
            }
        }
    }

    private int indiceDe(UUID idVerificacion, UUID idPatron) {
        for (int i = 0; i < patrones.size(); i++) {
            if (patrones.get(i).ocupa(idVerificacion, idPatron)) {
                return i;
            }
        }

        return -1;
    }

    /** Si las lecturas que se piden son exactamente las que ya están activas. */
    private boolean mismasLecturas(List<VerificationReading> pedidas) {
        List<VerificationReading> activas = lecturasActivas();

        return activas.size() == pedidas.size()
                && pedidas.stream()
                        .allMatch(pedida -> activas.stream().anyMatch(pedida::diceLoMismoQue));
    }

    private static List<VerificationReading> validarLecturas(Collection<VerificationReading> lecturas) {
        if (lecturas == null || lecturas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Registrar una verificacion sin lecturas no es registrar nada");
        }

        List<VerificationReading> pedidas = List.copyOf(lecturas);

        for (int i = 0; i < pedidas.size(); i++) {
            for (int j = i + 1; j < pedidas.size(); j++) {
                if (pedidas.get(i).ocupaElMismoSitioQue(pedidas.get(j))) {
                    throw new IllegalArgumentException(
                            "La lectura numero " + pedidas.get(i).secuencia()
                                    + " del punto " + pedidas.get(i).idPuntoVerificacion()
                                    + " esta registrada dos veces");
                }
            }
        }

        return pedidas;
    }

    /**
     * Un reporte cerrado no se toca, y uno retirado tampoco.
     *
     * <p>Se distinguen las dos negativas porque quien recibe el error necesita saber cuál es: un
     * reporte cerrado se corrige retirándolo y abriendo otro; uno retirado ya no se corrige.
     */
    private void exigirModificable(String accion) {
        if (!estadoActivo) {
            throw new IllegalStateException("No se puede " + accion + " en un reporte retirado");
        }
        if (estado.esFinal()) {
            throw new IllegalStateException("No se puede " + accion + " en un reporte ya cerrado:"
                    + " para corregirlo hay que retirarlo y abrir otro");
        }
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }

        String recortado = texto.trim();

        // El blanco borra: la base no admite la cadena vacia y distinguir "no se reporto falla" de "se
        // reporto una falla en blanco" no tiene sentido en algo que alguien va a leer impreso.
        return recortado.isEmpty() ? null : recortado;
    }

    private static UUID exigir(UUID valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException("Un reporte de servicio necesita su " + campo);
        }

        return valor;
    }
}
