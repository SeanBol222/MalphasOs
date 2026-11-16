package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.dataload.application.model.ExistingRecords;
import com.malphasos.malphasos.dataload.application.ports.output.ExistingRecordsPort;
import com.malphasos.malphasos.equipment.application.ports.output.BrandPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ManufacturerPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ModelPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderPersistencePort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee lo que ya existe con los puertos de persistencia de cada módulo. Lee todo de una vez: una carga
 * se valida pocas veces, y un catálogo de una empresa son cientos de filas, no millones.
 */
@Component
@RequiredArgsConstructor
public class ExistingRecordsAdapter implements ExistingRecordsPort {

    private final BrandPersistencePort marcas;
    private final ManufacturerPersistencePort fabricantes;
    private final ProtocolPersistencePort protocolos;
    private final EquipmentTypePersistencePort tipos;
    private final EquipmentPersistencePort asociaciones;
    private final ModelPersistencePort modelos;
    private final ReferenceStandardPersistencePort patrones;
    private final ClientEquipmentPersistencePort equipos;
    private final PersonPersistencePort personas;
    private final PersonSignaturePersistencePort firmas;
    private final WorkOrderPersistencePort ordenes;
    private final ClientPersistencePort clientes;
    private final HeadquarterPersistencePort sedes;
    private final ServiceAreaPersistencePort areas;

    @Override
    @Transactional(readOnly = true)
    public ExistingRecords read() {
        Map<UUID, String> marcaPorId = marcas.findAll().stream().filter(Brand::isEstadoActivo)
                .collect(Collectors.toMap(Brand::getId, Brand::getNombre));
        Map<UUID, String> tipoPorId = tipos.findAll().stream().filter(EquipmentType::isEstadoActivo)
                .collect(Collectors.toMap(EquipmentType::getId, EquipmentType::getNombre));
        Map<UUID, Equipment> asociacionPorId = asociaciones.findAll().stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        Map<UUID, ExistingRecords.ModelName> modeloPorId = modelos.findAll().stream().filter(Model::isEstadoActivo)
                .collect(Collectors.toMap(Model::getId, m -> {
                    Equipment asociacion = asociacionPorId.get(m.getIdEquipo());
                    return new ExistingRecords.ModelName(tipoPorId.get(asociacion.getIdTipoEquipo()),
                            marcaPorId.get(asociacion.getIdMarca()), m.getNombre());
                }));

        Map<UUID, List<ServiceArea>> areasPorSede = areas.findAll().stream().filter(ServiceArea::isEstadoActivo)
                .collect(Collectors.groupingBy(ServiceArea::getIdSede));
        Map<UUID, List<Headquarter>> sedesPorCliente = sedes.findAll().stream().filter(Headquarter::isEstadoActivo)
                .collect(Collectors.groupingBy(Headquarter::getIdCliente));
        Map<UUID, List<LocalDate>> fechasPorCliente = ordenes.findAll().stream().filter(WorkOrder::isEstadoActivo)
                .collect(Collectors.groupingBy(WorkOrder::getIdCliente,
                        Collectors.mapping(WorkOrder::getFechaMantenimiento, Collectors.toList())));
        List<Person> activas = personas.findAll().stream().filter(Person::isEstadoActivo).toList();
        List<Client> clientesActivos = clientes.findAll().stream().filter(Client::isEstadoActivo).toList();
        Map<UUID, String> documentoPorCliente = clientesActivos.stream()
                .collect(Collectors.toMap(Client::getId, Client::getDocumento));
        Map<UUID, UUID> clientePorSede = sedesPorCliente.values().stream().flatMap(List::stream)
                .collect(Collectors.toMap(Headquarter::getId, Headquarter::getIdCliente));
        Map<UUID, String> documentoPorArea = areasPorSede.values().stream().flatMap(List::stream)
                .filter(a -> clientePorSede.containsKey(a.getIdSede()))
                .collect(Collectors.toMap(ServiceArea::getId, a -> documentoPorCliente
                        .getOrDefault(clientePorSede.get(a.getIdSede()), "")));

        return new ExistingRecords(
                List.copyOf(marcaPorId.values()),
                fabricantes.findAll().stream().filter(Manufacturer::isEstadoActivo).map(Manufacturer::getNombre).toList(),
                protocolos.findAll().stream().filter(Protocol::isEstadoActivo).map(Protocol::getNombre).toList(),
                List.copyOf(tipoPorId.values()),
                List.copyOf(modeloPorId.values()),
                patrones.findAll().stream().filter(ReferenceStandard::isEstadoActivo).map(ReferenceStandard::getSerie).toList(),
                activas.stream().map(Person::getCedula).toList(),
                clientesActivos.stream().map(c -> new ExistingRecords.ExistingClient(
                        c.getDocumento(), sedesPorCliente.getOrDefault(c.getId(), List.of()).stream()
                                .map(s -> new ExistingRecords.ExistingSite(s.getNombre(), areasPorSede
                                        .getOrDefault(s.getId(), List.of()).stream().map(ServiceArea::getNombre).toList()))
                                .toList(), fechasPorCliente.getOrDefault(c.getId(), List.of())))
                        .toList(),
                equipos.findAll().stream().filter(ClientEquipment::isEstadoActivo)
                        .filter(e -> modeloPorId.containsKey(e.getIdModelo()))
                        .map(e -> new ExistingRecords.EquipmentName(modeloPorId.get(e.getIdModelo()), e.getSerie(),
                                documentoPorArea.get(e.getIdAreaServicio())))
                        .toList(),
                // Solo la gente de la casa, que es la que firma: una consulta por cada uno.
                activas.stream().filter(p -> p.getTipoPersona() == PersonType.ENGINEER || p.getTipoPersona() == PersonType.ADMIN)
                        .filter(p -> firmas.find(p.getIdentificador()).isPresent()).map(Person::getCedula).toList());
    }
}
