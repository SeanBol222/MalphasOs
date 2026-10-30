package com.malphasos.malphasos.workorder.application.services.workOrder;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentOverviewServicePort;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Compone el documento con el que una orden se congela al ejecutarse (2026-10-28, {@code V49}), leyendo
 * cada dato de donde vive hoy.
 *
 * <p><b>Tiene un gemelo en SQL</b>: el relleno de {@code V49}, que congeló las órdenes ejecutadas antes.
 * Los dos escriben la misma dirección, el mismo nombre del ingeniero y los equipos en el mismo orden, y
 * una prueba los compara sobre la misma orden. Por eso el orden de los equipos compara cadenas tal cual,
 * sin reglas del idioma: es lo que hace {@code COLLATE "C"}.
 *
 * <p>El área de cada equipo es la que la orden guardó al seleccionarlo, igual que lee el relleno.
 *
 * <p>Sin restricción de alcance: quien ejecuta una orden ya pasó por la autoridad de escribirla.
 */
@Component
public class WorkOrderDocumentComposer {

    /** El orden en que el documento lista los equipos. Lo comparte el relleno de V49. */
    static final Comparator<WorkOrderDocument.Equipo> ORDEN_DE_LOS_EQUIPOS = Comparator
            .comparing(WorkOrderDocument.Equipo::area, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(WorkOrderDocument.Equipo::numeroHojaVida, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(WorkOrderDocument.Equipo::serie, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(equipo -> equipo.id().toString());

    private final ClientServicePort clientServicePort;
    private final HeadquarterServicePort headquarterServicePort;
    private final ServiceAreaServicePort serviceAreaServicePort;
    private final CityServicePort cityServicePort;
    private final EquipmentOverviewServicePort equipmentOverviewServicePort;
    private final PersonCommunicationPort personCommunicationPort;
    private final WorkOrderReportsPort workOrderReportsPort;
    private final Clock reloj;

    /**
     * La fecha de ejecución es la de la zona de la empresa, no la del servidor: un contenedor corre en UTC,
     * y en Bogotá desde las siete de la noche eso ya es mañana. Es la misma zona que la hoja de vida.
     */
    @Autowired
    public WorkOrderDocumentComposer(
            ClientServicePort clientServicePort,
            HeadquarterServicePort headquarterServicePort,
            ServiceAreaServicePort serviceAreaServicePort,
            CityServicePort cityServicePort,
            EquipmentOverviewServicePort equipmentOverviewServicePort,
            PersonCommunicationPort personCommunicationPort,
            WorkOrderReportsPort workOrderReportsPort,
            @Value("${app.time-zone:America/Bogota}") String zona) {
        this(clientServicePort, headquarterServicePort, serviceAreaServicePort, cityServicePort,
                equipmentOverviewServicePort, personCommunicationPort, workOrderReportsPort,
                Clock.system(ZoneId.of(zona)));
    }

    WorkOrderDocumentComposer(
            ClientServicePort clientServicePort,
            HeadquarterServicePort headquarterServicePort,
            ServiceAreaServicePort serviceAreaServicePort,
            CityServicePort cityServicePort,
            EquipmentOverviewServicePort equipmentOverviewServicePort,
            PersonCommunicationPort personCommunicationPort,
            WorkOrderReportsPort workOrderReportsPort,
            Clock reloj) {
        this.clientServicePort = clientServicePort;
        this.headquarterServicePort = headquarterServicePort;
        this.serviceAreaServicePort = serviceAreaServicePort;
        this.cityServicePort = cityServicePort;
        this.equipmentOverviewServicePort = equipmentOverviewServicePort;
        this.personCommunicationPort = personCommunicationPort;
        this.workOrderReportsPort = workOrderReportsPort;
        this.reloj = reloj;
    }

    public WorkOrderDocument compose(WorkOrder orden) {
        Headquarter sede = headquarterServicePort.findById(orden.getIdSede(), ReadScope.unrestricted());
        Client cliente = clientServicePort.findById(sede.getIdCliente(), ReadScope.unrestricted());

        return new WorkOrderDocument(
                WorkOrderDocument.VERSION,
                orden.numero(),
                orden.getTipoServicio().name(),
                orden.isGarantia(),
                orden.getPeriodicidad().name(),
                orden.getFechaMantenimiento(),
                LocalDate.now(reloj),
                new WorkOrderDocument.Cliente(cliente.getRazonSocial(), cliente.getDocumento()),
                new WorkOrderDocument.Sede(
                        sede.getNombre(),
                        comoTexto(sede.getDireccion()),
                        cityServicePort.findById(sede.getIdCiudad()).getNombre()),
                orden.getIdIngeniero() == null
                        ? null
                        : personCommunicationPort.findById(orden.getIdIngeniero()).nombreCompleto(),
                equipos(orden));
    }

    private List<WorkOrderDocument.Equipo> equipos(WorkOrder orden) {
        Map<UUID, EquipmentOverview.Row> filas = filasDe(orden.getEquipos().stream()
                .map(SelectedEquipment::getIdEquipoCliente)
                .collect(Collectors.toSet()));
        Map<UUID, String> reportes = workOrderReportsPort.activeReportNumbers(orden.getId());
        Map<UUID, String> areas = new HashMap<>();

        return orden.getEquipos().stream()
                .map(seleccionado -> {
                    EquipmentOverview.Row fila = filas.get(seleccionado.getIdEquipoCliente());

                    return new WorkOrderDocument.Equipo(
                            seleccionado.getIdEquipoCliente(),
                            areas.computeIfAbsent(seleccionado.getIdAreaServicio(), id ->
                                    serviceAreaServicePort.findById(id, ReadScope.unrestricted()).getNombre()),
                            fila.tipoEquipo(),
                            fila.marca(),
                            fila.modelo(),
                            fila.serie(),
                            fila.numeroHojaVida(),
                            reportes.get(seleccionado.getIdEquipoCliente()));
                })
                .sorted(ORDEN_DE_LOS_EQUIPOS)
                .toList();
    }

    /** Lo que identifica a cada equipo, del listado pedido por identificadores, página a página. */
    private Map<UUID, EquipmentOverview.Row> filasDe(Set<UUID> ids) {
        Map<UUID, EquipmentOverview.Row> filas = new HashMap<>();
        if (ids.isEmpty()) {
            return filas;
        }

        List<EquipmentOverview.Row> leidas = new ArrayList<>();
        int pagina = 0;
        EquipmentOverview resultado;
        do {
            resultado = equipmentOverviewServicePort.list(new EquipmentOverviewQuery(
                    null, null, null, null, true, ids, pagina++, EquipmentOverviewQuery.TAMANO_MAXIMO),
                    ReadScope.unrestricted());
            leidas.addAll(resultado.filas());
        } while (leidas.size() < resultado.total() && !resultado.filas().isEmpty());

        leidas.forEach(fila -> filas.put(fila.id(), fila));

        return filas;
    }

    /** La dirección de la sede como la escribe la hoja de vida, y como la escribe el relleno de V49. */
    private static String comoTexto(Address direccion) {
        return "Calle " + direccion.calle() + " # " + direccion.carrera() + " - " + direccion.numero();
    }
}
