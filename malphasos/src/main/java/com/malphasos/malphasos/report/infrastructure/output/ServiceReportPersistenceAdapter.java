package com.malphasos.malphasos.report.infrastructure.output;

import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import com.malphasos.malphasos.report.infrastructure.output.mapper.ServiceReportPersistenceMapper;
import com.malphasos.malphasos.report.infrastructure.output.repository.ServiceReportRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda y recupera reportes de servicio.
 *
 * <p>Al guardar busca antes la fila existente y se la pasa al mapper, por lo mismo que los
 * adaptadores de {@code client} y de {@code work-order}: sin ella el mapper construiría una entidad
 * nueva en cada guardado y Hibernate insertaría duplicados de las lecturas.
 *
 * <p><b>Los métodos llevan {@code @Transactional}</b> porque el mapeo recorre las lecturas, que son
 * una colección perezosa: sin transacción abierta lanzaría {@code LazyInitializationException},
 * porque {@code open-in-view} está desactivado en este proyecto. Es el quinto adaptador con colección
 * propia y el segundo que lo lleva desde el primer día — en {@code work-order} faltó cuatro tandas y
 * no se vio hasta que una prueba llamó al adaptador por su cuenta.
 */
@Component
@RequiredArgsConstructor
public class ServiceReportPersistenceAdapter implements ServiceReportPersistencePort {

    private final ServiceReportRepository serviceReportRepository;
    private final ServiceReportPersistenceMapper mapper;

    /**
     * Guarda el reporte en <b>dos escrituras cuando ya existía</b>, y el orden es obligatorio.
     *
     * <p>Primero se vuelca lo que ya está en la base —incluida la lectura que se retira— y se vacía la
     * sesión; solo después se insertan las lecturas nuevas. Sin ese orden, corregir una lectura choca
     * contra {@code UQ_dato_verificacion_activo}: Hibernate vacía los {@code INSERT} antes que los
     * {@code UPDATE}, de modo que la base vería por un instante dos lecturas activas del mismo punto
     * con el mismo número. Lo destapó la prueba de este adaptador, y con dobles no habría aparecido.
     *
     * <p>Un reporte nuevo no paga nada: sin fila previa no hay nada que vaciar antes.
     */
    @Override
    @Transactional
    public ServiceReport save(ServiceReport reporte) {
        ServiceReportEntity existente =
                serviceReportRepository.findById(reporte.getId()).orElse(null);
        ServiceReportEntity entity = mapper.toEntity(reporte, existente);

        if (existente != null) {
            serviceReportRepository.saveAndFlush(entity);
        }

        mapper.addNewReadings(reporte, entity);

        return mapper.toDomain(serviceReportRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceReport> findById(UUID id) {
        return serviceReportRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo) {
        return mapper.toDomainList(serviceReportRepository.findByIdOrdenTrabajo(idOrdenTrabajo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByEquipment(UUID idEquipoCliente) {
        return mapper.toDomainList(serviceReportRepository.findByIdEquipoCliente(idEquipoCliente));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceReport> findActiveByWorkOrderAndEquipment(
            UUID idOrdenTrabajo, UUID idEquipoCliente) {

        return serviceReportRepository
                .findByIdOrdenTrabajoAndIdEquipoClienteAndEstadoActivoTrue(
                        idOrdenTrabajo, idEquipoCliente)
                .map(mapper::toDomain);
    }
}
