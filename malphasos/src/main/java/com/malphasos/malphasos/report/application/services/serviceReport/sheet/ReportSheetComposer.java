package com.malphasos.malphasos.report.application.services.serviceReport.sheet;

import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.SIN_DATO;
import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.conSigno;
import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.fechaCorta;
import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.numero;
import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.oRaya;

import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.application.model.ReportSheet;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compone el informe de un reporte tal como se imprime (2026-10-17): el formato «C · tablero con escudo»
 * que el usuario eligió el 2026-10-16, con las decisiones que tomó entonces. Hasta el 2026-10-17 esto lo
 * hacía el navegador, en {@code documento-reporte.ts}; con el PDF en el servidor (RF-17) se movió aquí
 * entero, para que el diseño viva en un solo sitio.
 *
 * <p><b>Lo que no se guarda, se deriva</b>, decidido por el usuario: el supervisor es el encargado del área,
 * el próximo servicio es la fecha más la periodicidad de la orden, y la casilla de verificación metrológica
 * se marca sola si hay lecturas.
 *
 * <p><b>Es un solo informe</b>: el título es siempre el mismo, el tipo de servicio lo dicen las casillas, y
 * cada sección sale solo si tiene datos. La página 2 existe si el reporte tiene lecturas.
 *
 * <p>Sin estado y sin dependencias: lo que imprime de los demás módulos ya viene en el informe congelado.
 */
public final class ReportSheetComposer {

    /**
     * El resultado como lo dice un informe entregado: apto o no apto. Se guardan tres valores y se imprimen
     * dos; la restricción va en las observaciones, que es donde el reporte la pide.
     */
    private static final Map<ServiceResult, String> APTITUD = Map.of(
            ServiceResult.OPERATIVO, "Apto para el servicio",
            ServiceResult.OPERATIVO_CON_RESTRICCIONES, "Apto para el servicio",
            ServiceResult.FUERA_DE_SERVICIO, "No apto para el servicio");

    private static final Map<String, Integer> MESES = Map.of(
            "MENSUAL", 1, "TRIMESTRAL", 3, "SEMESTRAL", 6, "ANUAL", 12);

    private static final Map<String, String> RIESGO = Map.of(
            "I", "Clase I", "IIA", "Clase IIa", "IIB", "Clase IIb", "III", "Clase III");

    private ReportSheetComposer() {
    }

    public static ReportSheet compose(ReportDocumentView vista) {
        ServiceReport reporte = vista.reporte();
        ReportDocument documento = vista.documento();
        ReportDocument.Orden orden = documento.orden();
        ReportDocument.Cliente cliente = documento.cliente();
        ReportDocument.Equipo equipo = documento.equipo();
        ReportDocument.Empresa empresa = documento.empresa();
        List<VerificationReading> lecturas = reporte.lecturasActivas();
        int paginas = lecturas.isEmpty() ? 1 : 2;

        LocalDate fechaDelServicio = reporte.getFinalizado() != null
                ? reporte.getFinalizado().toLocalDate()
                : orden.fechaProgramada();
        String resultado = reporte.getResultado() == null ? "Sin registrar" : APTITUD.get(reporte.getResultado());
        String verificacion = verificacion(lecturas);
        String supervisor = unidos(cliente.responsables());
        Handover recibe = reporte.getRecibe();

        return new ReportSheet(
                reporte.getEstado() != ReportState.FINALIZADO,
                reporte.numero(),
                orden.numero(),
                empresa == null ? null : empresa.nombre(),
                dataUri(vista.logo()),
                lineaDelEquipo(equipo),
                reporte.getId(),
                new ReportSheet.Estado(resultado, fechaCorta(fechaDelServicio),
                        fechaCorta(proximoServicio(fechaDelServicio, orden.periodicidad())), verificacion),
                new ReportSheet.Cliente(
                        cliente.nombre() + " · " + oRaya(cliente.documento()),
                        cliente.sede() + " · " + oRaya(cliente.ciudadSede()),
                        oRaya(cliente.direccionSede()),
                        supervisor,
                        unidos(cliente.telefonos()),
                        unidos(cliente.correos())),
                new ReportSheet.Equipo(
                        oRaya(equipo.marca()) + " · " + oRaya(equipo.modelo()),
                        oRaya(equipo.numeroInventario()) + " · " + oRaya(equipo.codigoInterno()),
                        cliente.area(),
                        oRaya(equipo.tecnologia()) + " · " + oRaya(equipo.uso()),
                        equipo.riesgo() == null ? SIN_DATO : RIESGO.getOrDefault(equipo.riesgo(), equipo.riesgo()),
                        alimentacion(equipo)),
                casillas(orden, lecturas),
                ingreso(reporte),
                pasos(reporte),
                relato(reporte),
                empresa == null ? null : vacioANulo(empresa.aclaracion()),
                reporte.getObservaciones() == null ? "Ninguna." : reporte.getObservaciones(),
                empresa == null ? null : vacioANulo(empresa.garantia()),
                new ReportSheet.Firmas(
                        oRaya(documento.ingeniero()),
                        dataUri(vista.firmaIngeniero()),
                        supervisor,
                        recibe == null ? SIN_DATO : oRaya(unir(" · ", recibe.nombre(),
                                recibe.cedula() == null ? null : "C.C. " + recibe.cedula())),
                        recibe == null || recibe.cargo() == null ? "Recibe" : "Recibe · " + recibe.cargo(),
                        dataUri(vista.firmaRecibe())),
                empresa == null ? "" : unir(" · ", empresa.codigoInforme(),
                        unir(", ", empresa.direccion(), empresa.ciudad()), empresa.movil()),
                paginas,
                trazabilidad(reporte, documento),
                medidas(reporte, documento),
                equipo.numeroHojaVida());
    }

    /**
     * El día del próximo servicio: el del servicio más la periodicidad, en meses de calendario. El 31 de
     * agosto más seis meses es el último día de febrero, no el 3 de marzo, que es lo que hace
     * {@link LocalDate#plusMonths}.
     */
    static LocalDate proximoServicio(LocalDate fecha, String periodicidad) {
        Integer meses = periodicidad == null ? null : MESES.get(periodicidad);

        return fecha == null || meses == null ? null : fecha.plusMonths(meses);
    }

    /**
     * «4 de 5 conformes». Solo cuentan las lecturas con tolerancia: sin ella no se sabe, y no se inventa.
     */
    static String verificacion(List<VerificationReading> lecturas) {
        if (lecturas.isEmpty()) {
            return "Sin verificación";
        }
        List<VerificationReading> juzgadas = lecturas.stream().filter(l -> l.conforme() != null).toList();
        if (juzgadas.isEmpty()) {
            return lecturas.size() + (lecturas.size() == 1 ? " lectura" : " lecturas");
        }

        return juzgadas.stream().filter(VerificationReading::conforme).count() + " de " + juzgadas.size()
                + " conformes";
    }

    /** «Termohigrómetro · Genérico Digital · Serie T-9 · HV-CDN-0014». */
    private static String lineaDelEquipo(ReportDocument.Equipo equipo) {
        return unir(" · ", equipo.tipo(), unir(" ", equipo.marca(), equipo.modelo()),
                equipo.serie() == null ? null : "Serie " + equipo.serie(), equipo.numeroHojaVida());
    }

    /** «AC · 110 V · 60 Hz»: lo que la ficha técnica del modelo diga, en ese orden. */
    private static String alimentacion(ReportDocument.Equipo equipo) {
        String linea = unir(" · ",
                equipo.alimentacion(),
                equipo.voltaje() == null ? null : equipo.voltaje() + " V",
                equipo.potencia() == null ? null : equipo.potencia() + " W",
                equipo.amperaje() == null ? null : numero(equipo.amperaje()) + " A",
                equipo.frecuencia() == null ? null : equipo.frecuencia() + " Hz");

        return oRaya(linea);
    }

    /**
     * Las cinco del informe: los tres tipos de orden, la verificación, que se marca sola, y la garantía,
     * que es una marca del correctivo en la orden (V34).
     */
    private static List<ReportSheet.Casilla> casillas(ReportDocument.Orden orden, List<VerificationReading> lecturas) {
        String tipo = orden.tipoServicio();

        return List.of(
                new ReportSheet.Casilla("Preventivo", "PREVENTIVO".equals(tipo)),
                new ReportSheet.Casilla("Correctivo", "CORRECTIVO".equals(tipo)),
                new ReportSheet.Casilla("Calibración", "CALIBRACION".equals(tipo)),
                new ReportSheet.Casilla("Verificación metrológica", !lecturas.isEmpty()),
                new ReportSheet.Casilla("Garantía", orden.garantia()));
    }

    /** Las dos filas fijas y los accesorios, con las palabras del informe impreso. */
    private static List<ReportSheet.FilaDeIngreso> ingreso(ServiceReport reporte) {
        List<ReportSheet.FilaDeIngreso> filas = new ArrayList<>();
        filas.add(filaDeIngreso("Estado de la estructura física del equipo", reporte.getIngresoFisico()));
        filas.add(filaDeIngreso("Estado operativo del equipo", reporte.getIngresoOperativo()));

        if (reporte.accesoriosActivos().isEmpty()) {
            filas.add(new ReportSheet.FilaDeIngreso("Accesorios", "No tiene", SIN_DATO, false));
        }
        reporte.accesoriosActivos().forEach(accesorio -> filas.add(new ReportSheet.FilaDeIngreso(
                "Accesorio: " + accesorio.nombre(),
                accesorio.conforme() ? "Conforme" : "No conforme",
                oRaya(accesorio.observacion()),
                true)));

        return filas;
    }

    private static ReportSheet.FilaDeIngreso filaDeIngreso(String texto, IntakeCheck verificacion) {
        if (verificacion == null) {
            return new ReportSheet.FilaDeIngreso(texto, "Sin registrar", SIN_DATO, false);
        }
        String estado = switch (verificacion.estado()) {
            case CONFORME -> "Conforme";
            case NO_CONFORME -> "No conforme";
            case NO_APLICA -> "No aplica";
        };

        return new ReportSheet.FilaDeIngreso(texto, estado, oRaya(verificacion.observacion()), false);
    }

    /** Los pasos del protocolo: ✓ hecho, «N/A» con su nota si no aplicó, en blanco si quedó pendiente. */
    private static List<ReportSheet.Paso> pasos(ServiceReport reporte) {
        return reporte.getPasos().stream()
                .map(paso -> new ReportSheet.Paso(
                        paso.estado() == StepState.HECHO ? "✓" : paso.estado() == StepState.NO_APLICA ? "N/A" : "",
                        paso.nombre(),
                        paso.estado() == StepState.NO_APLICA ? paso.nota() : null))
                .toList();
    }

    /** Lo que se escribió a mano, en el orden en que pasó. Solo lo que tiene texto. */
    private static List<ReportSheet.Parte> relato(ServiceReport reporte) {
        return Stream.of(
                        new ReportSheet.Parte("Falla reportada", reporte.getFallaReportada()),
                        new ReportSheet.Parte("Diagnóstico", reporte.getDiagnostico()),
                        new ReportSheet.Parte("Causa", reporte.getCausa()),
                        new ReportSheet.Parte(reporte.getPasos().isEmpty() ? "Procedimientos" : "Trabajo adicional",
                                reporte.getProcedimientos()),
                        new ReportSheet.Parte("Repuestos", reporte.getRepuestos()))
                .filter(parte -> parte.texto() != null && !parte.texto().isBlank())
                .toList();
    }

    /**
     * La trazabilidad: cada patrón una vez, aunque se usara en dos verificaciones, con las magnitudes en que
     * se usó. Con la ficha que se copió al ponerlo en el reporte (V30), no la de hoy.
     */
    private static List<ReportSheet.Patron> trazabilidad(ServiceReport reporte, ReportDocument documento) {
        Map<UUID, UsedReferenceStandard> porPatron = new LinkedHashMap<>();
        Map<UUID, List<String>> magnitudes = new LinkedHashMap<>();

        for (VerificationStandard usado : reporte.patronesActivos()) {
            porPatron.putIfAbsent(usado.patron().id(), usado.patron());
            magnitudes.computeIfAbsent(usado.patron().id(), id -> new ArrayList<>())
                    .add(magnitudDe(documento, usado.idVerificacion()));
        }

        return porPatron.values().stream()
                .map(patron -> new ReportSheet.Patron(
                        patron.nombre(),
                        unir(" · ", unir(" ", patron.marca(), patron.modelo()),
                                magnitudes.get(patron.id()).stream().filter(Objects::nonNull)
                                        .collect(Collectors.joining(" y "))),
                        patron.serie(),
                        patron.codigoInterno() == null ? "" : patron.codigoInterno(),
                        oRaya(patron.rango()),
                        patron.resolucion() == null ? "" : patron.resolucion(),
                        patron.certificado(),
                        fechaCorta(patron.fechaCalibracion()),
                        "vence " + fechaCorta(patron.vencimiento()),
                        oRaya(patron.empresaCalibradora())))
                .toList();
    }

    private static String magnitudDe(ReportDocument documento, UUID idVerificacion) {
        return documento.verificaciones().stream()
                .filter(verificacion -> verificacion.id().equals(idVerificacion))
                .map(ReportDocument.Verificacion::magnitud)
                .findFirst()
                .orElse(null);
    }

    /**
     * Una sección por verificación medida, en el orden en que el tipo las declara: su tabla, su gráfica y sus
     * patrones. Las lecturas se ordenan por punto y número; sin punto, por número —X1, X2—, que es como las
     * nombran los informes de la empresa.
     *
     * <p>El error y la conformidad son los de la lectura, y la tolerancia la que se copió en ella: si el tipo
     * la cambió después, el informe no cambia de veredicto.
     */
    private static List<ReportSheet.Medida> medidas(ServiceReport reporte, ReportDocument documento) {
        List<VerificationReading> lecturas = reporte.lecturasActivas();
        List<ReportDocument.Verificacion> declaradas = documento.verificaciones();
        Set<UUID> ids = new LinkedHashSet<>();
        lecturas.forEach(lectura -> ids.add(lectura.idVerificacion()));

        return ids.stream()
                .sorted(Comparator.comparingInt(id -> indiceOFinal(declaradas, id)))
                .map(id -> medida(reporte, lecturas, declaradas.stream()
                        .filter(verificacion -> verificacion.id().equals(id)).findFirst().orElse(null), id))
                .toList();
    }

    private static ReportSheet.Medida medida(
            ServiceReport reporte, List<VerificationReading> todas, ReportDocument.Verificacion declarada, UUID id) {

        Map<UUID, BigDecimal> puntos = new LinkedHashMap<>();
        if (declarada != null) {
            declarada.puntos().forEach(punto -> puntos.put(punto.id(), punto.valor()));
        }
        Comparator<VerificationReading> porPunto = Comparator.comparing(
                lectura -> lectura.idPuntoVerificacion() == null
                        ? BigDecimal.ZERO
                        : puntos.getOrDefault(lectura.idPuntoVerificacion(), BigDecimal.ZERO));
        List<VerificationReading> suyas = todas.stream()
                .filter(lectura -> lectura.idVerificacion().equals(id))
                .sorted(porPunto.thenComparingInt(VerificationReading::secuencia))
                .toList();

        String unidad = declarada != null && declarada.unidad() != null
                ? declarada.unidad()
                : suyas.isEmpty() || suyas.getFirst().unidad() == null ? "" : suyas.getFirst().unidad();
        BigDecimal tolerancia = suyas.stream().map(VerificationReading::tolerancia).filter(Objects::nonNull)
                .findFirst().orElse(null);
        boolean variasPorPunto = declarada != null && declarada.cantidadDatos() != null
                && declarada.cantidadDatos() > 1;
        List<VerificationReading> juzgadas = suyas.stream().filter(lectura -> lectura.conforme() != null).toList();

        List<ReportSheet.Fila> filas = suyas.stream()
                .map(lectura -> new ReportSheet.Fila(
                        lectura.idPuntoVerificacion() != null
                                ? numero(puntos.getOrDefault(lectura.idPuntoVerificacion(), BigDecimal.ZERO)) + " "
                                        + unidad + (variasPorPunto ? " · " + lectura.secuencia() : "")
                                : "X" + lectura.secuencia(),
                        numero(lectura.valorPatron()),
                        numero(lectura.valorEquipo()),
                        conSigno(lectura.error()),
                        lectura.conforme() == null ? SIN_DATO : lectura.conforme() ? "Sí" : "No",
                        Boolean.FALSE.equals(lectura.conforme())))
                .toList();

        return new ReportSheet.Medida(
                declarada == null ? "Verificación" : declarada.magnitud(),
                unidad,
                tolerancia == null ? "sin tolerancia" : "tolerancia ±" + numero(tolerancia) + " " + unidad,
                filas,
                juzgadas.isEmpty()
                        ? suyas.size() + " lecturas, sin tolerancia con que juzgarlas"
                        : juzgadas.stream().filter(VerificationReading::conforme).count() + " de " + juzgadas.size()
                                + " lecturas conformes",
                reporte.patronesActivos().stream()
                        .filter(usado -> usado.idVerificacion().equals(id))
                        .map(usado -> usado.patron().nombre())
                        .collect(Collectors.joining(" · ")),
                ErrorChart.of(suyas.stream()
                        .map(lectura -> new ErrorChart.Lectura(lectura.error(), lectura.conforme()))
                        .toList(), tolerancia),
                tolerancia == null
                        ? "Error de cada lectura (equipo − patrón). Sin tolerancia declarada: no hay banda."
                        : "Error de cada lectura (equipo − patrón) · banda gris: tolerancia ±" + numero(tolerancia)
                                + " " + unidad + " · en rojo, fuera de ella");
    }

    /** Lo que el tipo ya no declara va al final, no al principio. */
    private static int indiceOFinal(List<ReportDocument.Verificacion> declaradas, UUID id) {
        for (int i = 0; i < declaradas.size(); i++) {
            if (declaradas.get(i).id().equals(id)) {
                return i;
            }
        }

        return Integer.MAX_VALUE;
    }

    private static String unidos(List<String> valores) {
        return valores == null || valores.isEmpty() ? SIN_DATO : String.join(" · ", valores);
    }

    /** Las partes que tienen texto, unidas con el separador. */
    private static String unir(String separador, String... partes) {
        return Stream.of(partes).filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(separador));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    private static String dataUri(DocumentImage imagen) {
        return imagen == null ? null : imagen.dataUri();
    }
}
