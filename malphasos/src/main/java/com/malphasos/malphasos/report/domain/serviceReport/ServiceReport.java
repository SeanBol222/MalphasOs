package com.malphasos.malphasos.report.domain.serviceReport;

import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportDeactivatedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFilledEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportOpenedEvent;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportPayload;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportVerifiedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private boolean estadoActivo;

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
    }

    /** Abre el reporte de un equipo de una orden. Nace en borrador, vacío y sin lecturas. */
    public static ServiceReport open(UUID idOrdenTrabajo, UUID idEquipoCliente) {
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

        // Sin validar: leer de la base no es un hecho del dominio, y una fila vieja que ya no cumple una
        // regla nueva tiene que poder cargarse para poder corregirla.
        return new ServiceReport(id, idOrdenTrabajo, idEquipoCliente, estado, fallaReportada,
                diagnostico, procedimientos, observaciones, resultado, finalizado, lecturas,
                estadoActivo);
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
     * <p><b>Lo que no se comprueba aquí:</b> que un equipo verificable traiga sus lecturas. Saber si el
     * tipo de ese equipo se verifica exige consultar el módulo de equipos, y eso es del servicio.
     */
    public void finish() {
        exigirModificable("cerrar el reporte");

        if (procedimientos == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un reporte sin procedimientos: es lo que se hizo");
        }
        if (resultado == null) {
            throw new IllegalStateException(
                    "No se puede cerrar un reporte sin resultado: es como quedo el equipo, y de ahi"
                            + " cuelgan la hoja de vida y las alertas");
        }

        this.estado = ReportState.FINALIZADO;
        this.finalizado = LocalDateTime.now();
        registerEvent(new ServiceReportFinishedEvent(
                metadataFor(ServiceReportFinishedEvent.TYPE), payload()));
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
