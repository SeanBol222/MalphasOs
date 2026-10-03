package com.malphasos.malphasos.report.infrastructure.output.mapper;

import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import com.malphasos.malphasos.report.infrastructure.output.entities.VerificationReadingEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el reporte y sus filas.
 *
 * <p>A mano y no con MapStruct: el agregado se construye por {@code rehydrate} y no ofrece setters ni
 * builder a propósito.
 *
 * <p><b>La conciliación de las lecturas va en dos pasos, y el orden no es estético.</b> El agregado
 * conserva sus lecturas retiradas con el mismo identificador —{@code deactivated()} devuelve la misma
 * lectura con el estado en falso— y añade la corregida al lado, de modo que durante un instante hay
 * dos lecturas del mismo punto con el mismo número. <b>Hibernate vacía los {@code INSERT} antes que
 * los {@code UPDATE}</b>, así que en ese instante las dos están activas y el índice único parcial
 * {@code UQ_dato_verificacion_activo} salta. Lo destapó
 * {@code ServiceReportPersistenceAdapterTest.laLecturaCorregidaSeRetira}, y no habría aparecido nunca
 * con dobles.
 *
 * <p>Por eso {@link #toEntity} <b>solo pone al día las filas que ya están</b> —incluida la que se
 * retira— y las nuevas las inserta {@link #addNewReadings}, que el adaptador llama <b>después</b> de
 * vaciar la sesión. Un índice único parcial no se puede declarar diferido en PostgreSQL —{@code
 * DEFERRABLE} es de las restricciones, no de los índices—, así que el orden hay que imponerlo aquí.
 */
@Component
public class ServiceReportPersistenceMapper {

    public ServiceReport toDomain(ServiceReportEntity entity) {
        // Todas las lecturas, retiradas incluidas: el agregado distingue unas de otras y una lectura
        // corregida sigue formando parte de lo que paso.
        List<VerificationReading> lecturas = entity.getLecturas().stream()
                .map(fila -> VerificationReading.rehydrate(
                        fila.getId(),
                        fila.getIdVerificacion(),
                        fila.getIdPuntoVerificacion(),
                        fila.getSecuencia(),
                        fila.getValorPatron(),
                        fila.getValorEquipo(),
                        fila.getUnidad(),
                        fila.isEstadoActivo()))
                .toList();

        return ServiceReport.rehydrate(
                entity.getId(),
                entity.getIdOrdenTrabajo(),
                entity.getIdEquipoCliente(),
                ReportState.valueOf(entity.getEstado()),
                entity.getFallaReportada(),
                entity.getDiagnostico(),
                entity.getProcedimientos(),
                entity.getObservaciones(),
                entity.getResultado() == null ? null : ServiceResult.valueOf(entity.getResultado()),
                entity.getFinalizado(),
                lecturas,
                entity.isEstadoActivo());
    }

    public List<ServiceReport> toDomainList(List<ServiceReportEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * Vuelca el reporte sobre su fila.
     *
     * <p>Recibe la entidad existente cuando la hay, para conservar las filas hijas que JPA ya gestiona
     * y limitarse a ponerlas al día. Construir una entidad nueva en cada guardado haría que Hibernate
     * insertara duplicados de las lecturas, que es lo que le pasó al adaptador de clientes con sus
     * contactos y al de órdenes con su alcance.
     */
    public ServiceReportEntity toEntity(ServiceReport reporte, ServiceReportEntity existente) {
        ServiceReportEntity entity = existente != null ? existente : new ServiceReportEntity();

        entity.setId(reporte.getId());
        entity.setIdOrdenTrabajo(reporte.getIdOrdenTrabajo());
        entity.setIdEquipoCliente(reporte.getIdEquipoCliente());
        entity.setEstado(reporte.getEstado().name());
        entity.setFallaReportada(reporte.getFallaReportada());
        entity.setDiagnostico(reporte.getDiagnostico());
        entity.setProcedimientos(reporte.getProcedimientos());
        entity.setObservaciones(reporte.getObservaciones());
        entity.setResultado(reporte.getResultado() == null ? null : reporte.getResultado().name());
        entity.setFinalizado(reporte.getFinalizado());
        entity.setEstadoActivo(reporte.isEstadoActivo());

        actualizarLecturasExistentes(reporte, entity);

        return entity;
    }

    /**
     * Inserta las lecturas que el reporte trae y la fila todavía no tiene.
     *
     * <p>Va aparte de {@link #toEntity} a propósito: el adaptador vacía la sesión entre las dos
     * llamadas para que los {@code UPDATE} que retiran una lectura lleguen a la base <b>antes</b> que
     * el {@code INSERT} de la que la sustituye. Ver el javadoc de esta clase.
     */
    public void addNewReadings(ServiceReport reporte, ServiceReportEntity entity) {
        Map<UUID, VerificationReadingEntity> porId = indexarPorId(entity);

        for (VerificationReading lectura : reporte.getLecturas()) {
            if (porId.containsKey(lectura.id())) {
                continue;
            }

            VerificationReadingEntity fila = new VerificationReadingEntity();
            fila.setId(lectura.id());
            fila.setReporte(entity);
            volcar(lectura, fila);

            entity.getLecturas().add(fila);
        }
    }

    /**
     * Pone al día las filas de lecturas que ya existen, y solo esas.
     *
     * <p>Lo que cambia aquí es sobre todo el estado: una lectura pasa de activa a retirada cuando la
     * verificación se corrige. <b>Nada se borra</b>, y para eso no hace falta ninguna regla, porque el
     * agregado nunca quita una lectura de su lista.
     */
    private void actualizarLecturasExistentes(ServiceReport reporte, ServiceReportEntity entity) {
        Map<UUID, VerificationReading> porId = new LinkedHashMap<>();
        reporte.getLecturas().forEach(lectura -> porId.put(lectura.id(), lectura));

        for (VerificationReadingEntity fila : entity.getLecturas()) {
            VerificationReading lectura = porId.get(fila.getId());

            if (lectura != null) {
                volcar(lectura, fila);
            }
        }
    }

    private Map<UUID, VerificationReadingEntity> indexarPorId(ServiceReportEntity entity) {
        Map<UUID, VerificationReadingEntity> porId = new LinkedHashMap<>();
        entity.getLecturas().forEach(fila -> porId.put(fila.getId(), fila));

        return porId;
    }

    private void volcar(VerificationReading lectura, VerificationReadingEntity fila) {
        fila.setIdVerificacion(lectura.idVerificacion());
        fila.setIdPuntoVerificacion(lectura.idPuntoVerificacion());
        fila.setSecuencia(lectura.secuencia());
        fila.setValorPatron(lectura.valorPatron());
        fila.setValorEquipo(lectura.valorEquipo());
        fila.setUnidad(lectura.unidad());
        fila.setEstadoActivo(lectura.estadoActivo());
    }
}
