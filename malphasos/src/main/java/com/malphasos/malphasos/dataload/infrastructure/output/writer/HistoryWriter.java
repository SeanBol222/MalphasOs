package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import static com.malphasos.malphasos.dataload.infrastructure.output.writer.Steps.paso;

import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.ReportDocumentComposer;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Escribe el historial de un cliente: sus órdenes, <b>ya ejecutadas</b>, y sus reportes, <b>ya cerrados</b>
 * con su fecha antigua. Es la única parte de la carga que no puede entrar por los {@code create}: un
 * reporte no se cierra en el pasado, y uno histórico tiene lo que se hizo entonces —pasos de otra plantilla,
 * lecturas en puntos retirados, ninguna lectura—. Entran por su {@code rehydrate}, y lo que los valida es el
 * esquema, la validación y la base.
 *
 * <p>Después se compone cada documento con lo de hoy, como {@code freezeClosedWithoutDocument()} con los
 * cerrados antes de {@code V37}:
 * el del reporte con la firma guardada del ingeniero y marcado tardío, y el de la orden sin fecha de
 * ejecución, que es como queda una congelada tarde. <b>Lo único que no es de hoy es el certificado de cada
 * patrón</b>: el informe lleva el que estaba vigente el día del servicio, porque el catálogo solo guarda el
 * último (decidido por el usuario el 2026-11-07).
 *
 * <p>El número de una orden y el de un reporte son el comienzo de su identificador: se elige uno cuyo número
 * no esté tomado, en la base ni en la carga.
 */
@Component
class HistoryWriter {

    private final WorkOrderPersistencePort ordenes;
    private final ServiceReportPersistencePort reportes;
    private final WorkOrderServicePort ordenesServicio;
    private final ReportDocumentComposer compositor;
    private final DocumentImageStorePort imagenes;
    private final EquipmentTypePersistencePort tipos;
    private final EntityManager entityManager;
    private final ZoneId zona;

    HistoryWriter(WorkOrderPersistencePort ordenes, ServiceReportPersistencePort reportes,
            WorkOrderServicePort ordenesServicio, ReportDocumentComposer compositor, DocumentImageStorePort imagenes,
            EquipmentTypePersistencePort tipos, EntityManager entityManager,
            @Value("${app.time-zone:America/Bogota}") String zona) {
        this.ordenes = ordenes;
        this.reportes = reportes;
        this.ordenesServicio = ordenesServicio;
        this.compositor = compositor;
        this.imagenes = imagenes;
        this.tipos = tipos;
        this.entityManager = entityManager;
        this.zona = ZoneId.of(zona);
    }

    /** Una orden escrita y lo que hace falta para volver a guardarla con su documento. */
    private record Written(WorkOrder orden, LoadDocument.Order deLaCarga, String ruta) {}

    /**
     * Lo que un reporte trae, construido una vez: el borrador y el cerrado son el mismo reporte y comparten
     * sus lecturas, pasos y patrones con sus identificadores.
     */
    private record Parts(UUID id, UUID idOrden, LoadDocument.Report deLaCarga, List<VerificationStandard> patrones,
            List<VerificationReading> lecturas, List<ReportStep> pasos, List<ReportAccessory> accesorios,
            String ruta) {}

    void write(ReadFile archivo, LoadRefs refs) {
        String f = archivo.nombre();
        LoadDocument documento = archivo.documento();
        UUID idCliente = refs.id(documento.cliente());
        Set<String> numerosDeOrden = new HashSet<>();
        Set<String> numerosDeReporte = new HashSet<>();
        List<Written> escritas = new ArrayList<>();
        List<Parts> escritos = new ArrayList<>();

        for (int i = 0; i < documento.ordenes().size(); i++) {
            LoadDocument.Order orden = documento.ordenes().get(i);
            String ruta = "/ordenes/" + i;
            UUID idOrden = libre(ordenes::numberTaken, numerosDeOrden);
            WorkOrder escrita = paso(f, ruta, () -> ordenes.save(orden(orden, idOrden, idCliente, refs, null)));
            escritas.add(new Written(escrita, orden, ruta));

            for (int j = 0; j < orden.reportes().size(); j++) {
                LoadDocument.Report reporte = orden.reportes().get(j);
                String rutaReporte = ruta + "/reportes/" + j;
                UUID idReporte = libre(reportes::numberTaken, numerosDeReporte);
                Parts partes = paso(f, rutaReporte, () -> partes(reporte, idReporte, idOrden, refs, f, rutaReporte));
                paso(f, rutaReporte, () -> reportes.save(reporte(partes, refs, ReportState.BORRADOR, null)));
                escritos.add(partes);
            }
        }

        // Los compositores leen con consultas, y algunas son de JDBC: sin vaciar no verían lo escrito.
        entityManager.flush();

        // Los reportes entraron como borradores y se cierran ahora, cada uno con su documento: el informe lee
        // la hoja de vida de su equipo, cuyo historial exige que todo reporte cerrado tenga documento (V51),
        // y mientras se compone el primero los demás todavía no lo tienen.
        for (Parts partes : escritos) {
            paso(f, partes.ruta(), () -> {
                ServiceReport cerrado = reporte(partes, refs, ReportState.FINALIZADO, null);
                return reportes.save(reporte(partes, refs, ReportState.FINALIZADO, congelar(cerrado, refs)));
            });
        }
        for (Written escrita : escritas) {
            paso(f, escrita.ruta(), () -> {
                WorkOrderDocument hoy = ordenesServicio.documentOf(escrita.orden());
                return ordenes.save(orden(escrita.deLaCarga(), escrita.orden().getId(), idCliente, refs, hoy));
            });
        }
    }

    /** Un identificador cuyo número —sus seis primeros caracteres— no tiene nadie. */
    private static UUID libre(Predicate<String> tomado, Set<String> enLaCarga) {
        return libre(tomado, enLaCarga, UUID::randomUUID);
    }

    static UUID libre(Predicate<String> tomado, Set<String> enLaCarga, Supplier<UUID> generador) {
        while (true) {
            UUID id = generador.get();
            String numero = id.toString().substring(0, WorkOrder.LARGO_NUMERO).toUpperCase(Locale.ROOT);
            if (!enLaCarga.contains(numero) && !tomado.test(numero)) {
                enLaCarga.add(numero);
                return id;
            }
        }
    }

    /**
     * La orden de la carga. Sin documento entra en ejecución, y con él, ejecutada y congelada tarde: la base
     * exige que una ejecutada tenga su documento (V49), y el documento se compone con sus reportes ya
     * escritos. Las dos cosas pasan en la misma transacción, así que nadie la ve a medias.
     */
    private static WorkOrder orden(LoadDocument.Order orden, UUID id, UUID idCliente, LoadRefs refs,
            WorkOrderDocument documento) {
        return WorkOrder.rehydrate(id, idCliente, refs.id(orden.sede()), orden.fecha(),
                Periodicity.valueOf(orden.periodicidad()), ServiceType.valueOf(orden.tipoServicio()),
                Boolean.TRUE.equals(orden.garantia()),
                documento == null ? ExecutionState.EN_EJECUCION : ExecutionState.EJECUTADA, refs.id(orden.ingeniero()),
                true, documento, documento != null);
    }

    private Parts partes(LoadDocument.Report reporte, UUID id, UUID idOrden, LoadRefs refs, String archivo,
            String ruta) {
        List<VerificationStandard> patrones = new ArrayList<>();
        List<VerificationReading> lecturas = new ArrayList<>();
        for (int v = 0; v < reporte.verificaciones().size(); v++) {
            LoadDocument.ReportVerification verificacion = reporte.verificaciones().get(v);
            LoadRefs.VerificationRef suya = refs.verification(verificacion.verificacion());
            UUID idVerificacion = suya.verificacion().id();
            verificacion.patrones().forEach(p -> patrones.add(
                    new VerificationStandard(UUID.randomUUID(), idVerificacion, refs.id(p), true)));

            // El número de cada lectura cuenta dentro de su punto: tres lecturas en el punto 50 son la 1, la 2 y la 3.
            Map<UUID, Integer> secuencia = new HashMap<>();
            for (int l = 0; l < verificacion.lecturas().size(); l++) {
                LoadDocument.Reading lectura = verificacion.lecturas().get(l);
                String rutaLectura = ruta + "/verificaciones/" + v + "/lecturas/" + l + "/punto";
                UUID idPunto = lectura.punto() == null ? null : suya.verificacion().puntos().stream()
                        .filter(p -> p.valor().compareTo(lectura.punto()) == 0)
                        .map(VerificationPoint::id)
                        .findFirst()
                        .orElseThrow(() -> Steps.rechazo(archivo, rutaLectura,
                                "La lectura en el punto " + lectura.punto().toPlainString() + " no está entre los puntos de"
                                        + " hoy ni los anteriores de su verificación: se declara en puntosAnteriores"));
                int numero = secuencia.merge(idPunto == null ? idVerificacion : idPunto, 1, Integer::sum);
                lecturas.add(VerificationReading.rehydrate(UUID.randomUUID(), idVerificacion, idPunto, numero,
                        lectura.patron(), lectura.equipo(), true));
            }
        }

        List<ReportAccessory> accesorios = new ArrayList<>();
        for (int a = 0; a < reporte.ingreso().accesorios().size(); a++) {
            LoadDocument.Accessory accesorio = reporte.ingreso().accesorios().get(a);
            accesorios.add(ReportAccessory.of(a + 1, accesorio.nombre(), Boolean.TRUE.equals(accesorio.conforme()),
                    accesorio.observacion()));
        }
        List<ReportStep> pasos = reporte.pasos().stream()
                .map(p -> new ReportStep(UUID.randomUUID(), refs.id(p.protocolo()), StepState.valueOf(p.estado()), p.nota()))
                .toList();

        return new Parts(id, idOrden, reporte, patrones, lecturas, pasos, accesorios, ruta);
    }

    /**
     * El reporte en un estado: borrador sin fecha de cierre, o cerrado en su día. Con documento, congelado
     * tarde, como los cerrados antes de que el informe se congelara.
     */
    private ServiceReport reporte(Parts partes, LoadRefs refs, ReportState estado, ReportDocument documento) {
        LoadDocument.Report reporte = partes.deLaCarga();
        LoadDocument.Receiver recibe = reporte.recibe();

        return ServiceReport.rehydrate(partes.id(), partes.idOrden(), refs.id(reporte.equipo()), estado,
                reporte.fallaReportada(), reporte.diagnostico(), reporte.procedimientos(), reporte.observaciones(),
                reporte.causa(), reporte.repuestos(), partes.patrones(), ingreso(reporte.ingreso().fisico()),
                ingreso(reporte.ingreso().operativo()), partes.accesorios(),
                recibe == null || recibe.nombre() == null ? null : new Handover(recibe.nombre(), recibe.cedula(), recibe.cargo()),
                ServiceResult.valueOf(reporte.resultado()),
                estado == ReportState.FINALIZADO ? finalizado(reporte.finalizado()) : null, partes.lecturas(),
                partes.pasos(), true, documento, documento != null, null,
                recibe == null || recibe.persona() == null ? null : refs.id(recibe.persona()));
    }

    private static IntakeCheck ingreso(LoadDocument.IntakeState estado) {
        return new IntakeCheck(IntakeState.valueOf(estado.estado()), estado.observacion());
    }

    /** Una fecha sola se toma a mediodía, como dice el formato; una con hora, en la zona de la empresa. */
    private LocalDateTime finalizado(String valor) {
        if (valor.length() == 10) {
            return LocalDate.parse(valor).atTime(LocalTime.NOON);
        }
        return OffsetDateTime.parse(valor).atZoneSameInstant(zona).toLocalDateTime();
    }

    /** El documento de hoy, con el certificado de cada patrón que estaba vigente el día del servicio. */
    private ReportDocument congelar(ServiceReport reporte, LoadRefs refs) {
        EquipmentType tipo = tipos.findById(refs.typeOfEquipment(reporte.getIdEquipoCliente())).orElseThrow();
        ReportDocumentComposer.Composed compuesto = compositor.compose(reporte, tipo);
        if (compuesto.logo() != null) {
            imagenes.store(compuesto.logo());
        }
        if (compuesto.firmaIngeniero() != null) {
            imagenes.store(compuesto.firmaIngeniero());
        }

        LocalDate dia = reporte.getFinalizado().toLocalDate();
        ReportDocument hoy = compuesto.documento();
        List<ReportDocument.Patron> patrones = hoy.patrones().stream()
                .map(p -> new ReportDocument.Patron(p.idVerificacion(), vigenteEl(dia, p.ficha(), refs)))
                .toList();
        return new ReportDocument(hoy.version(), hoy.orden(), hoy.cliente(), hoy.equipo(), hoy.verificaciones(),
                hoy.empresa(), hoy.ingeniero(), hoy.huellaFirmaIngeniero(), hoy.pasos(), patrones);
    }

    /**
     * El certificado vigente ese día: el más reciente calibrado hasta entonces y que no había vencido. Si
     * ninguno lo estaba, el último calibrado antes; y si se calibró después, el primero. El informe imprime
     * lo que hubo, no lo que debió haber.
     */
    static UsedReferenceStandard vigenteEl(LocalDate dia, UsedReferenceStandard hoy, LoadRefs refs) {
        LoadRefs.StandardRef patron = refs.standardById(hoy.id());
        if (patron == null || patron.certificados().isEmpty()) {
            return hoy;
        }
        Comparator<LoadDocument.Certificate> porCalibracion = Comparator.comparing(
                LoadDocument.Certificate::fechaCalibracion, Comparator.nullsFirst(Comparator.naturalOrder()));
        List<LoadDocument.Certificate> antes = patron.certificados().stream()
                .filter(c -> c.fechaCalibracion() == null || !c.fechaCalibracion().isAfter(dia))
                .toList();
        LoadDocument.Certificate elegido = antes.stream().filter(c -> !dia.isAfter(c.vencimiento())).max(porCalibracion)
                .or(() -> antes.stream().max(porCalibracion))
                .orElseGet(() -> patron.certificados().stream().min(porCalibracion).orElseThrow());

        return new UsedReferenceStandard(hoy.id(), hoy.nombre(), hoy.marca(), hoy.serie(), elegido.numero(),
                elegido.vencimiento(), hoy.modelo(), hoy.codigoInterno(), hoy.rango(), hoy.resolucion(),
                elegido.fechaCalibracion(), elegido.empresaCalibradora());
    }
}
